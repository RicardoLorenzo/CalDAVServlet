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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.LockedObject;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocks;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

public class UNLOCK extends CalDAVMethods {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVStore _store;
    private ResourceLocks _resource_locks;

    public UNLOCK(CalDAVStore store, ResourceLocks resourceLocks) {
        this._store = store;
        this._resource_locks = resourceLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
    	logger.debug("entry:  " + req.getRequestURI());
        String path = getRelativePath(req);
        String tempLockOwner = "UNLOCK" + System.currentTimeMillis() + req.toString();
        try {
            if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
                String lockId = getLockIdFromLockTokenHeader(req);
                LockedObject lo;
                if (lockId != null && ((lo = this._resource_locks.getLockedObjectByID(transaction, lockId)) != null)) {
                    String[] owners = lo.getOwner();
                    String owner = null;
                    if (lo.isShared()) {
                        // more than one owner is possible
                        if (owners != null) {
                            for (int i = 0; i < owners.length; i++) {
                                // remove owner from LockedObject
                                lo.removeLockedObjectOwner(owners[i]);
                            }
                        }
                    } else {
                        // exclusive, only one lock owner
                        if (owners != null)
                            owner = owners[0];
                        else
                            owner = null;
                    }

                    if (this._resource_locks.unlock(transaction, lockId, owner)) {
                        StoredObject so = this._store.getStoredObject(transaction, path);
                        if (so.isNullResource()) {
                            this._store.removeObject(transaction, path);
                        }

                        resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
                    } else {
                        resp.sendError(CalDAVResponse.SC_METHOD_FAILURE);
                    }
                } else {
                    resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
                }
            }
        } catch (LockException e) {
        	logger.error("unlock", e);
            // nothing
        } finally {
            this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
        }
    }
}