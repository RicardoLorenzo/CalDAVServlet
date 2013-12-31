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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.LockedObject;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocks;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

public class MKCOL extends CalDAVAbstractMethod {
    private CalDAVStore _store;
    private ResourceLocks _resource_locks;
    private CalDAVResourceACL resource_acl;

    public MKCOL(CalDAVStore store, ResourceLocks resourceLocks) {
        this._store = store;
        this._resource_locks = resourceLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        String path = getRelativePath(req);
        String parentPath = getParentPath(getCleanPath(path));
        this.resource_acl = this._store.getResourceACL(transaction, path);

        if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath)) {
            resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            return;
        }

        String tempLockOwner = "MKCOL" + System.currentTimeMillis() + req.toString();
        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            try {
                this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(), "read");

                mkcol(transaction, req, resp);
            } catch (AccessDeniedException e) {
                sendPrivilegeError(resp, path, e.getMessage());
            } catch (IOException e) {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void mkcol(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        Map<String, Integer> errorList = new HashMap<String, Integer>();
        String path = getRelativePath(req);
        String parentPath = getParentPath(getCleanPath(path));
        StoredObject parentSo, so = null;

        parentSo = this._store.getStoredObject(transaction, parentPath);
        if (parentPath != null && parentSo != null && parentSo.isFolder()) {
            so = this._store.getStoredObject(transaction, path);
            if (so == null) {
                this._store.createFolder(transaction, path);
                this._store.getResourceACL(transaction, path);
                resp.setStatus(CalDAVResponse.SC_CREATED);
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
                    if (lockTokens != null)
                        lockToken = lockTokens[0];
                    else {
                        resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                        return;
                    }
                    if (lockToken.equals(nullResourceLockToken)) {
                        so.setNullResource(false);
                        so.setFolder(true);

                        String[] nullResourceLockOwners = nullResourceLo.getOwner();
                        String owner = null;
                        if (nullResourceLockOwners != null)
                            owner = nullResourceLockOwners[0];

                        if (this._resource_locks.unlock(transaction, lockToken, owner)) {
                            resp.setStatus(CalDAVResponse.SC_CREATED);
                        } else {
                            resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        }
                    } else {
                        // TODO remove
                        errorList.put(path, CalDAVResponse.SC_LOCKED);
                        sendReport(req, resp, errorList);
                    }
                } else {
                    String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
                    resp.addHeader("Allow", methodsAllowed);
                    resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
                }
            }
        } else if (parentPath != null && parentSo != null && parentSo.isResource()) {
            // TODO remove
            String methodsAllowed = CalDAVMethods.determineMethodsAllowed(parentSo);
            resp.addHeader("Allow", methodsAllowed);
            resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
        } else if (parentPath != null && parentSo == null) {
            errorList.put(parentPath, CalDAVResponse.SC_NOT_FOUND);
            sendReport(req, resp, errorList);
        } else {
            resp.sendError(CalDAVResponse.SC_FORBIDDEN);
        }
    }
}
