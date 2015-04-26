/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ricardolorenzo.network.http.caldav.method;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ricardolorenzo.file.xml.XMLReader;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.CalDAVServlet;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.security.CalDAVPrincipal;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVPrivilege;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVPrivilegeCollection;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

/**
 * @author Ricardo Lorenzo
 * 
 */
public class ACL extends CalDAVAbstractMethod {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;
    private CalDAVResourceACL resource_acl;
    private int _depth;

    public ACL(CalDAVStore store, ResourceLocksMap resLocks) {
        this._store = store;
        this._resource_locks = resLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	logger.debug("entry: " + req.getRequestURI());

        String path = getCleanPath(getRelativePath(req));
        String tempLockOwner = "ACL" + System.currentTimeMillis() + req.toString();
        this.resource_acl = this._store.getResourceACL(transaction, path);
        this._depth = getDepth(req);

        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, this._depth, TEMP_TIMEOUT, TEMPORARY)) {
            StoredObject so = null;
            try {
                this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(CalDAVServlet.securityProvider.getUserPrincipal(req), "write-acl");
                so = this._store.getStoredObject(transaction, path);
                if (so == null) {
                    resp.setContentType("text/xml; charset=UTF-8");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                    return;
                }

                path = getCleanPath(getRelativePath(req));

                if (req.getContentLength() > 0) {
                    try {
                        Document document = XMLReader.getDocument(req.getInputStream());
                        logger.debug("body" + document.toString());
                        Element root = document.getDocumentElement();

                        CalDAVPrivilegeCollection collection = this.resource_acl.getPrivilegeCollection();
                        CalDAVResourceACL calendarResource = null;

                        if (!"acl".equals(XMLReader.getLocalName(root))) {
                            resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                        } else {

                            if (so.isFolder()) {
                                String calendarPath = path;
                                if (!calendarPath.endsWith("/")) {
                                    calendarPath = calendarPath.concat("/");
                                }
                                calendarPath += "calendar.ics";

                                if (this._store.getStoredObject(transaction, calendarPath) != null) {
                                    calendarResource = this._store.getResourceACL(transaction, calendarPath);
                                }
                            }
                            for (Node n : XMLReader.getChildElements(root, "ace")) {
                                CalDAVPrivilege privilege = new CalDAVPrivilege();
                                for (Node nn : XMLReader.getChildElements(n)) {
                                    if ("principal".equals(XMLReader.getLocalName(nn))) {
                                        Node nnn = XMLReader.firstSubElement(nn, "href");
                                        if (nnn != null) {
                                            privilege.setPrincipal(new CalDAVPrincipal(nnn.getTextContent()));
                                        }
                                    } else if ("grant".equals(XMLReader.getLocalName(nn))) {
                                        Node nnn = XMLReader.firstSubElement(nn, "privilege");
                                        if (nnn != null) {
                                            privilege.setGrantPrivilege(XMLReader.getLocalName(nnn.getFirstChild()));
                                        }
                                    } else if ("deny".equals(XMLReader.getLocalName(nn))) {
                                        Node nnn = XMLReader.firstSubElement(nn, "privilege");
                                        if (nnn != null) {
                                            privilege.setDenyPrivilege(XMLReader.getLocalName(nnn.getFirstChild()));
                                        }
                                    }
                                }

                                CalDAVPrivilege priv = collection.getPrincipalPrivilege(privilege.getPrincipal());
                                if (priv != null) {
                                    List<String> grants = priv.getGrantedPrivileges();
                                    for (String grant : privilege.getGrantedPrivileges()) {
                                        if (!grants.contains(grant)) {
                                            grants.add(grant);
                                        }
                                    }
                                    for (String deny : privilege.getDeniedPrivileges()) {
                                        grants.remove(deny);
                                    }

                                    privilege.setGrantPrivileges(grants);
                                    privilege.removeAllDeniedPrivileges();
                                }

                                collection.setPrivilege(privilege);
                            }
                        }

                        if (calendarResource != null) {
                            calendarResource.setPrivilegeCollection(transaction, collection);
                        }
                        this.resource_acl.setPrivilegeCollection(transaction, collection);
                    } catch (IOException e) {
                    	
                    	logger.error("acl", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (ParserConfigurationException e) {
                    	logger.error("acl", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (SAXException e) {
                    	logger.error("acl", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }
                }
                resp.setStatus(CalDAVResponse.SC_OK);
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            Map<String, Integer> errorList = new HashMap<String, Integer>();
            errorList.put(path, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
        }
    }
}
