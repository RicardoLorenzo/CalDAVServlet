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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.ObjectAlreadyExistsException;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;

public class MOVE extends CalDAVAbstractMethod {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private ResourceLocksMap _resource_locks;
    private DELETE _delete;
    private COPY _copy;

    public MOVE(ResourceLocksMap resourceLocks, DELETE delete, COPY copy) {
        this._resource_locks = resourceLocks;
        this._delete = delete;
        this._copy = copy;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
    	logger.debug("entry: " + req.getRequestURI());

        String sourcePath = getRelativePath(req);
        Map<String, Integer> errorList = new HashMap<String, Integer>();

        if (!checkLocks(transaction, req, resp, this._resource_locks, sourcePath)) {
            errorList.put(sourcePath, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return;
        }

        String destinationPath = req.getHeader("Destination");
        if (destinationPath == null) {
            resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
            return;
        }

        if (!checkLocks(transaction, req, resp, this._resource_locks, destinationPath)) {
            errorList.put(destinationPath, CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
            return;
        }

        String tempLockOwner = "MOVE" + System.currentTimeMillis() + req.toString();

        if (this._resource_locks.lock(transaction, sourcePath, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            try {
                if (this._copy.copyResource(transaction, req, resp)) {
                    errorList = new HashMap<String, Integer>();
                    this._delete.deleteResource(transaction, sourcePath, errorList, req, resp);
                    if (!errorList.isEmpty()) {
                        sendReport(req, resp, errorList);
                    }
                }
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (ObjectAlreadyExistsException e) {
                resp.sendError(CalDAVResponse.SC_NOT_FOUND, req.getRequestURI());
            } catch (CalDAVException e) {
            	logger.error("move", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                this._resource_locks.unlockTemporaryLockedObjects(transaction, sourcePath, tempLockOwner);
            }
        } else {
            errorList.put(req.getHeader("Destination"), CalDAVResponse.SC_LOCKED);
            sendReport(req, resp, errorList);
        }
    }
}
