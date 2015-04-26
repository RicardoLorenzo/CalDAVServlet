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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ricardolorenzo.file.xml.XMLReader;
import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;
import com.ricardolorenzo.icalendar.VTimeZone;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

/**
 * @author Ricardo Lorenzo
 * 
 */
public class MKCALENDAR extends CalDAVAbstractMethod {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;
    private CalDAVResourceACL _resource_acl;
    private MKCOL _mkcol;

    public MKCALENDAR(CalDAVStore store, ResourceLocksMap resLocks, MKCOL _mkcol) {
        this._store = store;
        this._resource_locks = resLocks;
        this._mkcol = _mkcol;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
    	logger.debug("entry: " + req.getRequestURI());

        String path = getCleanPath(getRelativePath(req));
        String tempLockOwner = "MKCALENDAR" + System.currentTimeMillis() + req.toString();
        this._resource_acl = this._store.getResourceACL(transaction, path);

        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            try {
            	this._resource_acl.getPrivilegeCollection()
                    .checkPrincipalPrivilege(transaction.getPrincipal(), "write");

                VTimeZone _vtz = new VTimeZone(null);
                
                // Check if the optional body has been sent.
                if (req.getContentLength() > 0) {
                    Document document = XMLReader.getDocument(req.getInputStream());
                    Element root = document.getDocumentElement();

                    if ("mkcalendar".equals(XMLReader.getLocalName(root))) {
                        Node timezone = XMLReader.findFirstSubElement(root, "calendar-timezone");
                        if (timezone != null) {
                            VCalendar _vc = new VCalendar(timezone.getTextContent());
                            _vtz = _vc.getTimeZone();
                        } else {
                            _vtz = new VTimeZone(TimeZone.getDefault().getDisplayName());
                        }
                    }
                }

                this._mkcol.mkcol(transaction, req, resp);
                path = path + "/calendar.ics";
                StringBuilder _sb = new StringBuilder();
                _sb.append("BEGIN:VCALENDAR\r\n");
                _sb.append(_vtz.toString());
                _sb.append("END:VCALENDAR\r\n");
                ByteArrayInputStream _bais = new ByteArrayInputStream(_sb.toString().getBytes());
                this._store.createResource(transaction, path);
                long length = this._store.setResourceContent(transaction, path, _bais, null, null);

                StoredObject so = this._store.getStoredObject(transaction, path);
                if (length != -1) {
                    so.setResourceLength(length);
                }

                this._store.getResourceACL(transaction, path);
            } catch (IOException e) {
            	logger.error("mkcalendar", e);
                resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                return;
            } catch (DOMException e) {
            	logger.error("mkcalendar", e);
                resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                return;
            } catch (VCalendarException e) {
            	logger.error("mkcalendar", e);
                resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                return;
            } catch (ParserConfigurationException e) {
            	logger.error("mkcalendar", e);
                resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                return;
            } catch (SAXException e) {
            	logger.error("mkcalendar", e);
                resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                return;
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (CalDAVException e) {
            	logger.error("mkcalendar", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
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
