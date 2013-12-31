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
import com.ricardolorenzo.network.http.caldav.CalDAVMimeType;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.ObjectAlreadyExistsException;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;

public class HEAD extends CalDAVAbstractMethod {
    protected String _draft_index_file;
    protected CalDAVStore _store;
    protected String _insteadOf404;
    protected ResourceLocksMap _resource_locks;
    private CalDAVResourceACL resource_acl;
    protected CalDAVMimeType _mime_type;
    protected int _content_length;

    public HEAD(CalDAVStore store, String draft_index_file, String insteadOf404, ResourceLocksMap resourceLocks,
            CalDAVMimeType mimeType, int contentLengthHeader) {
        this._store = store;
        this._draft_index_file = draft_index_file;
        this._insteadOf404 = insteadOf404;
        this._resource_locks = resourceLocks;
        this._mime_type = mimeType;
        this._content_length = contentLengthHeader;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        // determines if the uri exists.
        boolean uri_exists = false;

        String path = getRelativePath(req);
        this.resource_acl = this._store.getResourceACL(transaction, path);

        StoredObject so = this._store.getStoredObject(transaction, path);
        if (so == null) {
            if (this._insteadOf404 != null && !this._insteadOf404.trim().equals("")) {
                path = this._insteadOf404;
                so = this._store.getStoredObject(transaction, this._insteadOf404);
            }
        } else {
            uri_exists = true;
        }

        if (so != null) {
            if (so.isFolder()) {
                if (this._draft_index_file != null && !this._draft_index_file.trim().equals("")) {
                    resp.sendRedirect(resp.encodeRedirectURL(req.getRequestURI() + this._draft_index_file));
                    return;
                }
            } else if (so.isNullResource()) {
                String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
                resp.addHeader("Allow", methodsAllowed);
                resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }

            String tempLockOwner = "GET" + System.currentTimeMillis() + req.toString();

            if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
                try {
                    this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(), "read");
                    String eTagMatch = req.getHeader("If-None-Match");
                    if (eTagMatch != null) {
                        if (eTagMatch.equals(getETag(so))) {
                            resp.setStatus(CalDAVResponse.SC_NOT_MODIFIED);
                            return;
                        }
                    }

                    if (so.isResource()) {
                        // path points to a file but ends with / or \
                        if (path.endsWith("/") || (path.endsWith("\\"))) {
                            resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                        } else {
                            // setting headers
                            long lastModified = so.getLastModified().getTime();
                            resp.setDateHeader("last-modified", lastModified);

                            String eTag = getETag(so);
                            resp.addHeader("ETag", eTag);

                            long resourceLength = so.getResourceLength();

                            if (this._content_length == 1) {
                                if (resourceLength > 0) {
                                    if (resourceLength <= Integer.MAX_VALUE) {
                                        resp.setContentLength((int) resourceLength);
                                    } else {
                                        resp.setHeader("content-length", String.valueOf(resourceLength));
                                        // is "content-length" the right header?
                                        // is long a valid format?
                                    }
                                }
                            }

                            String mimeType = this._mime_type.getMimeType(path);
                            if (mimeType != null) {
                                resp.setContentType(mimeType);
                            } else {
                                int lastSlash = path.replace('\\', '/').lastIndexOf('/');
                                int lastDot = path.indexOf(".", lastSlash);
                                if (lastDot == -1) {
                                    resp.setContentType("text/html");
                                }
                            }
                            doBody(transaction, resp, path);
                        }
                    } else {
                        folderBody(transaction, path, resp, req);
                    }
                } catch (AccessDeniedException e) {
                    sendPrivilegeError(resp, path, e.getMessage());
                } catch (ObjectAlreadyExistsException e) {
                    resp.sendError(CalDAVResponse.SC_NOT_FOUND, req.getRequestURI());
                } catch (IOException e) {
                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                } finally {
                    this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
                }
            } else {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            folderBody(transaction, path, resp, req);
        }

        if (!uri_exists) {
            resp.setStatus(CalDAVResponse.SC_NOT_FOUND);
        }
    }

    protected void folderBody(CalDAVTransaction transaction, String path, HttpServletResponse resp,
            HttpServletRequest req) throws IOException {
        // no body for HEAD
    }

    protected void doBody(CalDAVTransaction transaction, HttpServletResponse resp, String path) throws IOException {
        // no body for HEAD
    }
}