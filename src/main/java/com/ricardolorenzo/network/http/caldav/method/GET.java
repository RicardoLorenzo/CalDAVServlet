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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVMimeType;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;
import com.ricardolorenzo.network.http.caldav.store.VCalendarCache;

public class GET extends HEAD {
    public GET(CalDAVStore store, String draft_index_file, String insteadOf404, ResourceLocksMap resourceLocks,
            CalDAVMimeType mimeType, int contentLengthHeader) {
        super(store, draft_index_file, insteadOf404, resourceLocks, mimeType, contentLengthHeader);
    }

    protected void doBody(CalDAVTransaction transaction, HttpServletResponse resp, String path) {
        try {
            StoredObject so = this._store.getStoredObject(transaction, path);
            if (so.isNullResource()) {
                String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
                resp.addHeader("Allow", methodsAllowed);
                resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            OutputStream out = resp.getOutputStream();
            InputStream in = this._store.getResourceContent(transaction, path);
            try {
                int read = -1;
                byte[] copyBuffer = new byte[BUF_SIZE];

                while ((read = in.read(copyBuffer, 0, copyBuffer.length)) != -1) {
                    out.write(copyBuffer, 0, read);
                }
            } finally {
                // flushing causes a IOE if a file is opened on the webserver
                // client disconnected before server finished sending response
                try {
                    in.close();
                } catch (IOException e) {
                }
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                }
            }
        } catch (AccessDeniedException e) {
            try {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (IOException e1) {
                // nothing
            }
        } catch (IOException e) {
            // nothing
        }
    }

    protected void folderBody(CalDAVTransaction transaction, String path, HttpServletResponse resp,
            HttpServletRequest req) throws IOException {
        StoredObject so = this._store.getStoredObject(transaction, path);
        if (so == null) {
            String parentPath = getParentPath(path);
            while (parentPath.endsWith(".ics")) {
                parentPath = parentPath.substring(0, parentPath.lastIndexOf("/"));
            }
            path = parentPath + path.substring(path.lastIndexOf("/"));

            if (path.endsWith(".ics")) {
                String calendarPath = parentPath.concat("/calendar.ics");
                String uid = path.substring(path.lastIndexOf("/") + 1);
                uid = uid.substring(0, uid.length() - 4);

                OutputStream _out = resp.getOutputStream();
                try {
                    if (this._store.resourceExists(transaction, calendarPath)) {
                        File _f = new File(this._store.getRootPath() + calendarPath);
                        VCalendar _vc = VCalendarCache.getVCalendar(_f);
                        if (_vc.hasVevent(uid) || _vc.hasVtodo(uid)) {
                            VCalendar _res_vc = new VCalendar();
                            if (_vc.hasVevent(uid)) {
                                _res_vc.addVevent(_vc.getVevent(uid));
                            } else if (_vc.hasVtodo(uid)) {
                                _res_vc.addVtodo(_vc.getVtodo(uid));
                            }
                            _out.write(_res_vc.toString().getBytes());
                        } else {
                            _out.write(_vc.toString().getBytes());
                        }
                    } else {
                        resp.sendError(CalDAVResponse.SC_NOT_FOUND);
                    }
                } catch (IOException e) {
                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    return;
                } catch (VCalendarException e) {
                    resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    return;
                } finally {
                    _out.flush();
                    _out.close();
                }
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
            }
        } else {
            if (so.isNullResource()) {
                String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
                resp.addHeader("Allow", methodsAllowed);
                resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }

            if (so.isFolder()) {
                // TODO some folder response (for browsers, DAV tools
                // use propfind) in html?
                OutputStream _out = resp.getOutputStream();
                String[] children = this._store.getChildrenNames(transaction, path);
                StringBuilder childrenTemp = new StringBuilder();
                childrenTemp.append("Contents of this Folder:\n");
                for (String child : children) {
                    childrenTemp.append(child);
                    childrenTemp.append("\n");
                }
                _out.write(childrenTemp.toString().getBytes());
                _out.flush();
                _out.close();
            }
        }
    }
}