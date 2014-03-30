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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ricardolorenzo.file.xml.XMLReader;
import com.ricardolorenzo.file.xml.XMLWriter;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.LockedObject;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class PROPPATCH extends CalDAVAbstractMethod {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;

    public PROPPATCH(CalDAVStore store, ResourceLocksMap resLocks) {
        this._store = store;
        this._resource_locks = resLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        String path = getRelativePath(req);
        String parentPath = getParentPath(getCleanPath(path));

        Map<String, Integer> errorList = new HashMap<String, Integer>();

        if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath)) {
            errorList.put(parentPath, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return; // parent is locked
        }

        if (!checkLocks(transaction, req, resp, this._resource_locks, path)) {
            errorList.put(path, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return; // resource is locked
        }

        // TODO for now, PROPPATCH just sends a valid response, stating that
        // everything is fine, but doesn't do anything.

        // Retrieve the resources
        String tempLockOwner = "PROPATCH" + System.currentTimeMillis() + req.toString();

        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            StoredObject so = null;
            LockedObject lo = null;
            try {
                // check ACL

                so = this._store.getStoredObject(transaction, path);
                lo = this._resource_locks.getLockedObjectByPath(transaction, getCleanPath(path));

                if (so == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                    // we do not to continue since there is no root
                    // resource
                }

                if (so.isNullResource()) {
                    String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
                    resp.addHeader("Allow", methodsAllowed);
                    resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
                    return;
                }

                if (lo != null && lo.isExclusive()) {
                    // Object on specified path is LOCKED
                    errorList = new HashMap<String, Integer>();
                    errorList.put(path, new Integer(CalDAVResponse.SC_LOCKED));
                    sendReport(req, resp, errorList);
                    return;
                }

                List<String> toset = null;
                List<String> toremove = null;
                List<String> tochange = new ArrayList<String>();
                // contains all properties from
                // toset and toremove

                path = getCleanPath(getRelativePath(req));

                Node tosetNode = null;
                Node toremoveNode = null;

                if (req.getContentLength() != 0) {
                    try {
                        Document document = XMLReader.getDocument(req.getInputStream());
                        tosetNode = XMLReader.firstSubElement(
                                XMLReader.firstSubElement(document.getDocumentElement(), "set"), "prop");
                        toremoveNode = XMLReader.firstSubElement(
                                XMLReader.firstSubElement(document.getDocumentElement(), "remove"), "prop");
                    } catch (IOException e) {
                    	logger.error("proppatch", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (ParserConfigurationException e) {
                    	logger.error("proppatch", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (SAXException e) {
                    	logger.error("proppatch", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }
                } else {
                	logger.error("Unable to retrieve content length for " + path);
                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }

                if (tosetNode != null) {
                    toset = XMLReader.getProperties(tosetNode);
                    tochange.addAll(toset);
                }

                if (toremoveNode != null) {
                    toremove = XMLReader.getProperties(toremoveNode);
                    tochange.addAll(toremove);
                }

                resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);
                resp.setContentType("text/xml; charset=UTF-8");

                try {
                    XMLWriter XML = new XMLWriter();
                    XML.setNameSpace("DAV:", "D");
                    XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

                    XML.addChildElement("D:multistatus");
                    XML.addChildElement("D:response");
                    String status = new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
                            + CalDAVResponse.getStatusText(CalDAVResponse.SC_OK));

                    // Generating href element
                    XML.addChildElement("D:href");

                    String href = req.getContextPath();
                    if ((href.endsWith("/")) && (path.startsWith("/"))) {
                        href += path.substring(1);
                    } else {
                        href += path;
                    }
                    if ((so.isFolder()) && (!href.endsWith("/"))) {
                        href += "/";
                    }

                    XML.setTextContent(rewriteUrl(href));
                    XML.closeElement();

                    for (String property : tochange) {
                        XML.addChildElement("D:propstat");

                        XML.addChildElement("D:prop");
                        XML.addProperty(property);
                        XML.closeElement();

                        XML.addChildElement("D:status");
                        XML.setTextContent(status);
                        XML.closeElement();

                        XML.closeElement();
                    }

                    XML.closeElement();

                    XML.closeElement();

                    XML.write(resp.getOutputStream());
                } catch (IOException e) {
                	logger.error("proppatch", e);
                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                } catch (ParserConfigurationException e) {
                	logger.error("proppatch", e);
                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (CalDAVException e) {
            	logger.error("proppatch", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
        	logger.error("Unable to retreive lock for " + path);
            resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
