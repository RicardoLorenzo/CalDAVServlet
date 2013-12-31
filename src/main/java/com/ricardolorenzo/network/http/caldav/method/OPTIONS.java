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

import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

public class OPTIONS extends CalDAVMethods {
    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;

    public OPTIONS(CalDAVStore store, ResourceLocksMap resLocks) {
        this._store = store;
        this._resource_locks = resLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        String tempLockOwner = "OPTIONS" + System.currentTimeMillis() + req.toString();
        String path = getRelativePath(req);
        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            StoredObject so = null;
            try {
                resp.addHeader("DAV", "1, 2, access-control, calendar-access");

                so = this._store.getStoredObject(transaction, path);
                String methodsAllowed = determineMethodsAllowed(so);
                resp.addHeader("Allow", methodsAllowed);
                resp.addHeader("MS-Author-Via", "DAV");
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (CalDAVException e) {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}