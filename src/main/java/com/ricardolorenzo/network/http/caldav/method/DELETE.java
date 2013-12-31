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
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ricardolorenzo.file.lock.FileLockException;
import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.ObjectAlreadyExistsException;
import com.ricardolorenzo.network.http.caldav.ObjectNotFoundException;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;
import com.ricardolorenzo.network.http.caldav.store.VCalendarCache;

public class DELETE extends CalDAVAbstractMethod {
    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;
    private CalDAVResourceACL resource_acl;

    public DELETE(CalDAVStore store, ResourceLocksMap resourceLocks) {
        this._store = store;
        this._resource_locks = resourceLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {

        String path = getRelativePath(req);
        String parentPath = getParentPath(getCleanPath(path));
        /*
         * Enable Mozilla SunBird updates
         */
        if (parentPath.endsWith(".ics")) {
            while (parentPath.endsWith(".ics")) {
                parentPath = parentPath.substring(0, parentPath.lastIndexOf("/"));
            }
            path = parentPath + path.substring(path.lastIndexOf("/"));
        }

        Map<String, Integer> errorList = new HashMap<String, Integer>();

        if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath)) {
            errorList.put(parentPath, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return;
        }

        if (!checkLocks(transaction, req, resp, this._resource_locks, path)) {
            errorList.put(path, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return;
        }

        String tempLockOwner = "doDelete" + System.currentTimeMillis() + req.toString();
        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            try {
                errorList = new HashMap<String, Integer>();

                if (path.endsWith(".ics")) {
                    StoredObject so = this._store.getStoredObject(transaction, path);
                    if (so == null) {
                        String uid = path.substring(path.lastIndexOf("/") + 1);
                        if (uid.endsWith(".ics")) {
                            uid = uid.substring(0, uid.length() - 4);
                        }

                        String href = path.substring(0, path.lastIndexOf("/"));
                        if (href.endsWith(".ics")) {
                            href = href.substring(0, href.lastIndexOf("/"));
                        }
                        href = href.concat("/calendar.ics");

                        this.resource_acl = this._store.getResourceACL(transaction, href);
                        this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(),
                                "write");

                        File _f = new File(this._store.getRootPath() + href);
                        VCalendar _vc = VCalendarCache.getVCalendar(_f);
                        _vc.removeVevent(uid);
                        _vc.removeVtodo(uid);
                        VCalendarCache.putVCalendar(_vc, _f);
                        this._store.setResourceContent(transaction, href, new ByteArrayInputStream(_vc.toString()
                                .getBytes()), "text/calendar", null);
                        resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
                    } else {
                        deleteResource(transaction, path, errorList, req, resp);
                    }
                } else {
                    deleteResource(transaction, path, errorList, req, resp);
                }

                if (!errorList.isEmpty()) {
                    sendReport(req, resp, errorList);
                }
            } catch (AccessDeniedException e) {
                sendPrivilegeError(resp, path, e.getMessage());
            } catch (ObjectAlreadyExistsException e) {
                resp.sendError(CalDAVResponse.SC_NOT_FOUND, req.getRequestURI());
            } catch (IOException e) {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (VCalendarException e) {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (FileLockException e) {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Elimina los recursos en la ruta "path"
     * 
     * @param transaction
     *            indica que el metodo se encuentra en el contexto de una transacci&oacute;n CalDAV
     * @param path
     *            la carpeta a eliminar
     * @param errorList
     *            la lista de errores ocurridos
     * @param req
     *            HttpServletRequest
     * @param resp
     *            HttpServletResponse
     * @throws CalDAVException
     * @throws IOException
     */
    public void deleteResource(CalDAVTransaction transaction, String path, Map<String, Integer> errorList,
            HttpServletRequest req, HttpServletResponse resp) throws IOException, CalDAVException {
        this.resource_acl = this._store.getResourceACL(transaction, path);
        this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(), "write");

        resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
        StoredObject so = this._store.getStoredObject(transaction, path);
        if (so != null) {
            if (so.isResource()) {
                this._store.removeObject(transaction, path);
                this.resource_acl.removeCollection(transaction);
            } else {
                if (so.isFolder()) {
                    deleteFolder(transaction, path, errorList, req, resp);
                    this._store.removeObject(transaction, path);
                } else {
                    resp.sendError(CalDAVResponse.SC_NOT_FOUND);
                }
            }
        } else {
            resp.sendError(CalDAVResponse.SC_NOT_FOUND);
        }
        so = null;
    }

    /**
     * 
     * Auxiliary method deleteResource() deletes the folder and all the content
     * 
     * @param transaction
     *            CalDAV transaction indication
     * @param path
     *            Folder path
     * @param errorList
     *            Errors list
     * @param req
     *            HttpServletRequest
     * @param resp
     *            HttpServletResponse
     * @throws CalDAVExceptions
     */
    private void deleteFolder(CalDAVTransaction transaction, String path, Map<String, Integer> errorList,
            HttpServletRequest req, HttpServletResponse resp) throws IOException, CalDAVException {
        List<String> _childrens = Arrays.asList(this._store.getAllChildrenNames(transaction, path));
        StoredObject so = null;
        for (String children : _childrens) {
            try {
                so = this._store.getStoredObject(transaction, path + children);
                if (so.isResource()) {
                    this._store.removeObject(transaction, path + children);
                } else {
                    deleteFolder(transaction, path + children, errorList, req, resp);
                    this._store.removeObject(transaction, path + children);
                }
            } catch (AccessDeniedException e) {
                sendPrivilegeError(resp, path, e.getMessage());
            } catch (ObjectNotFoundException e) {
                errorList.put(path + children, new Integer(CalDAVResponse.SC_NOT_FOUND));
            } catch (IOException e) {
                errorList.put(path + children, new Integer(CalDAVResponse.SC_INTERNAL_SERVER_ERROR));
            } catch (CalDAVException e) {
                errorList.put(path + children, new Integer(CalDAVResponse.SC_INTERNAL_SERVER_ERROR));
            }
        }
        so = null;
    }
}