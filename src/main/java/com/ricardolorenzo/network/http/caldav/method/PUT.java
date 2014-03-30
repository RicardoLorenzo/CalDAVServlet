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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.file.io.IOStreamUtils;
import com.ricardolorenzo.file.lock.FileLockException;
import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;
import com.ricardolorenzo.icalendar.VEvent;
import com.ricardolorenzo.icalendar.VTodo;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.CalDAVServlet;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.LockedObject;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocks;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVPrivilegeCollection;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;
import com.ricardolorenzo.network.http.caldav.store.VCalendarCache;

public class PUT extends CalDAVAbstractMethod {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVStore _store;
    private CalDAVResourceACL resource_acl;
    private ResourceLocks _resource_locks;
    private boolean _lazyFolderCreationOnPut;

    private String _userAgent;

    public PUT(CalDAVStore store, ResourceLocks resLocks, boolean lazyFolderCreationOnPut) {
        this._store = store;
        this._resource_locks = resLocks;
        this._lazyFolderCreationOnPut = lazyFolderCreationOnPut;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        String path = getRelativePath(req);
        String parentPath = getParentPath(path);

        this._userAgent = req.getHeader("User-Agent");
        this.resource_acl = this._store.getResourceACL(transaction, parentPath);

        Map<String, Integer> errorList = new HashMap<String, Integer>();

        if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath)) {
            sendReport(req, resp, errorList);
            return; // parent is locked
        }

        if (!checkLocks(transaction, req, resp, this._resource_locks, path)) {
            errorList.put(path, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return; // resource is locked
        }

        String tempLockOwner = "PUT" + System.currentTimeMillis() + req.toString();
        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            StoredObject parentSo, so = null;
            try {
                CalDAVPrivilegeCollection collection = this.resource_acl.getPrivilegeCollection();
                collection.checkPrincipalPrivilege(CalDAVServlet.securityProvider.getUserPrincipal(req), "write");

                parentSo = this._store.getStoredObject(transaction, parentPath);
                if (parentPath != null && parentSo != null && parentSo.isResource()) {
                    resp.sendError(CalDAVResponse.SC_FORBIDDEN);
                    return;
                } else if (parentPath != null && parentSo == null && parentPath.endsWith(".ics")) {
                    /*
                     * Enable Mozilla SunBird updates
                     */
                    while (parentPath.endsWith(".ics")) {
                        parentPath = parentPath.substring(0, parentPath.lastIndexOf("/"));
                    }
                    path = parentPath + path.substring(path.lastIndexOf("/"));
                } else if (parentPath != null && parentSo == null && this._lazyFolderCreationOnPut) {
                    this._store.createFolder(transaction, parentPath);
                } else if (parentPath != null && parentSo == null && !this._lazyFolderCreationOnPut) {
                    errorList.put(parentPath, CalDAVResponse.SC_NOT_FOUND);
                    sendReport(req, resp, errorList);
                    return;
                }

                so = this._store.getStoredObject(transaction, path);
                if (!path.endsWith(".ics")) {
                    if (so == null) {
                        this._store.createResource(transaction, path);
                    } else {
                        if (so.isNullResource()) {
                            LockedObject nullResourceLo = this._resource_locks.getLockedObjectByPath(transaction, path);
                            if (nullResourceLo == null) {
                                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                                return;
                            }
                            String nullResourceLockToken = nullResourceLo.getID();
                            String[] lockTokens = getLockIdFromIfHeader(req);
                            String lockToken = null;
                            if (lockTokens != null) {
                                lockToken = lockTokens[0];
                            } else {
                                resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                                return;
                            }
                            if (lockToken.equals(nullResourceLockToken)) {
                                so.setNullResource(false);
                                so.setFolder(false);

                                String[] nullResourceLockOwners = nullResourceLo.getOwner();
                                String owner = null;
                                if (nullResourceLockOwners != null) {
                                    owner = nullResourceLockOwners[0];
                                }
                                if (!this._resource_locks.unlock(transaction, lockToken, owner)) {
                                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                                }
                            } else {
                                errorList.put(path, CalDAVResponse.SC_LOCKED);
                                sendReport(req, resp, errorList);
                            }
                        }
                    }
                }

                // User-Agent workarounds
                doUserAgentWorkaround(resp);

                long length = -1;
                if (path.endsWith(".ics")) {
                    String calendarPath = parentPath.concat("/calendar.ics");
                    File _f = new File(this._store.getRootPath() + calendarPath);
                    InputStream is = req.getInputStream();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    IOStreamUtils.write(is, os);
                    byte[] buffer = os.toByteArray();
                    IOStreamUtils.closeQuietly(is);
                    IOStreamUtils.closeQuietly(os);

                    VCalendar _vc = VCalendarCache.getVCalendar(_f);
                    VCalendar _req_vc = new VCalendar(new ByteArrayInputStream(buffer));
                    for (VEvent ve : _req_vc.getVevents()) {
                        if (!ve.hasLastModified()) {
                            ve.setLastModified(Calendar.getInstance());
                        }
                        _vc.addVevent(ve);
                    }
                    for (VTodo vt : _req_vc.getVtodos()) {
                        if (!vt.hasLastModified()) {
                            vt.setLastModified(Calendar.getInstance());
                        }
                        _vc.addVtodo(vt);
                    }

                    VCalendarCache.putVCalendar(_vc, _f);
                    this._store.setResourceContent(transaction, calendarPath, new ByteArrayInputStream(_vc.toString()
                            .getBytes()), "text/calendar", null);
                } else {
                    length = this._store.setResourceContent(transaction, path, req.getInputStream(), null, null);
                }

                so = this._store.getStoredObject(transaction, path);
                if (length != -1) {
                    so.setResourceLength(length);
                }

                this._store.getResourceACL(transaction, path);

                // Now lets report back what was actually saved
                // Webdav Client Goliath executes 2 PUTs with the same
                // resource when contentLength is added to response
                if (this._userAgent != null && this._userAgent.indexOf("Goliath") != -1) {
                    // nada
                } else {
                    resp.setContentLength((int) length);
                }
            } catch (AccessDeniedException e) {
                sendPrivilegeError(resp, path, e.getMessage());
            } catch (IOException e) {
            	logger.error("put", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (VCalendarException e) {
            	logger.error("put", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (FileLockException e) {
            	logger.error("put", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
        	logger.error("Unable to retrieve lock for " + path);
            resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param resp
     */
    private void doUserAgentWorkaround(HttpServletResponse resp) {
        if (this._userAgent != null && this._userAgent.indexOf("WebDAVFS") != -1
                && _userAgent.indexOf("Transmit") == -1) {
            resp.setStatus(CalDAVResponse.SC_CREATED);
        } else if (this._userAgent != null && this._userAgent.indexOf("Transmit") != -1) {
            // Transmit also uses WEBDAVFS 1.x.x but crashes
            // with SC_CREATED response
            resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
        } else {
            resp.setStatus(CalDAVResponse.SC_CREATED);
        }
    }
}
