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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import com.ricardolorenzo.icalendar.VAction;
import com.ricardolorenzo.network.http.caldav.CalDAVMethod;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.LockedObject;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocks;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;
import com.ricardolorenzo.xml.XMLWriter;

public abstract class CalDAVAbstractMethod implements CalDAVMethod {
    protected static final int INFINITY = 3;
    protected static final SimpleDateFormat CREATION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    protected static final SimpleDateFormat LAST_MODIFIED_DATE_FORMAT = new SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

    static {
        CREATION_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        LAST_MODIFIED_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    protected static int BUF_SIZE = 65536;
    protected static final int DEFAULT_TIMEOUT = 3600;
    protected static final int MAX_TIMEOUT = 604800;
    protected static final boolean TEMPORARY = true;
    protected static final int TEMP_TIMEOUT = 10;

    /**
     * Returns the relative path of the servlet.
     * 
     * @param request
     *            Servlet request.
     */
    protected String getRelativePath(HttpServletRequest request) {
        if (request.getAttribute("javax.servlet.include.request_uri") != null) {
            String result = String.valueOf(request.getAttribute("javax.servlet.include.path_info"));
            if ((result == null) || (result.equals("")))
                result = "/";
            return result;
        }

        String result = request.getPathInfo();
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return result;
    }

    /**
     * Creates the parent path from the given path by removing the last '/' and everything after
     * that
     * 
     * @param path
     *            the path
     * @return parent path
     */
    protected String getParentPath(String path) {
        int slash = path.lastIndexOf('/');
        if (slash != -1) {
            return path.substring(0, slash);
        }
        return null;
    }

    /**
     * Removes a / at the end of the path string, if present
     * 
     * @param path
     *            the path
     * @return the path without trailing /
     */
    protected String getCleanPath(String path) {
        if (path.endsWith("/") && path.length() > 1)
            path = path.substring(0, path.length() - 1);
        return path;
    }

    /**
     * reads the depth header from the request and returns it as a int
     * 
     * @param req
     * @return the depth from the depth header
     */
    protected int getDepth(HttpServletRequest req) {
        int depth = INFINITY;
        String depthStr = req.getHeader("Depth");
        if (depthStr != null) {
            if (depthStr.equals("0")) {
                depth = 0;
            } else if (depthStr.equals("1")) {
                depth = 1;
            }
        }
        return depth;
    }

    /**
     * URL rewriter.
     * 
     * @param path
     *            Path which has to be rewiten
     * @return the rewritten path
     * @throws UnsupportedEncodingException
     */
    protected String rewriteUrl(String path) throws UnsupportedEncodingException {
        return URLEncoder.encode(path, "UTF-8");
    }

    /**
     * Get the ETag associated with a file.
     * 
     * @param StoredObject
     *            StoredObject to get resourceLength, lastModified and a hashCode of StoredObject
     * @return the ETag
     */
    protected String getETag(StoredObject so) {
        StringBuilder _sb = new StringBuilder();
        _sb.append("\"");
        if (so != null && so.isResource()) {
            _sb.append(so.getResourceLength());
            _sb.append("-");
            _sb.append(so.getLastModified().getTime());
        } else {
            _sb.append("0000-0000000000000000");
        }
        _sb.append("\"");
        return _sb.toString();
    }

    /**
     * Get the ETag associated with a file.
     * 
     * @param StoredObject
     *            StoredObject to get resourceLength, lastModified and a hashCode of StoredObject
     * @return the ETag
     */
    protected String getETag(VAction va) {
        StringBuilder _sb = new StringBuilder();
        _sb.append("\"");
        if (va != null && va.hasLastModified()) {
            _sb.append(va.toString().length());
            _sb.append("-");
            _sb.append(va.getLastModified().getTimeInMillis());
        } else {
            _sb.append("0000-0000000000000000");
        }
        _sb.append("\"");
        return _sb.toString();
    }

    /**
     * Get the CTag associated with a file.
     * 
     * @param StoredObject
     *            StoredObject to get resourceLength, lastModified and a hashCode of StoredObject
     * @return the CTag
     */
    protected String getCTag(StoredObject so) {
        StringBuilder _sb = new StringBuilder();
        if (so != null && so.isResource()) {
            _sb.append(so.getResourceLength());
            _sb.append("-");
            _sb.append(so.getLastModified().getTime());
        } else {
            _sb.append("0000-0000000000000000");
        }
        return _sb.toString();
    }

    protected String[] getLockIdFromIfHeader(HttpServletRequest req) {
        String[] ids = new String[2];
        String id = req.getHeader("If");

        if (id != null && !id.equals("")) {
            if (id.indexOf(">)") == id.lastIndexOf(">)")) {
                id = id.substring(id.indexOf("(<"), id.indexOf(">)"));

                if (id.indexOf("locktoken:") != -1) {
                    id = id.substring(id.indexOf(':') + 1);
                }
                ids[0] = id;
            } else {
                String firstId = id.substring(id.indexOf("(<"), id.indexOf(">)"));
                if (firstId.indexOf("locktoken:") != -1) {
                    firstId = firstId.substring(firstId.indexOf(':') + 1);
                }
                ids[0] = firstId;

                String secondId = id.substring(id.lastIndexOf("(<"), id.lastIndexOf(">)"));
                if (secondId.indexOf("locktoken:") != -1) {
                    secondId = secondId.substring(secondId.indexOf(':') + 1);
                }
                ids[1] = secondId;
            }

        } else {
            ids = null;
        }
        return ids;
    }

    protected String getLockIdFromLockTokenHeader(HttpServletRequest req) {
        String id = req.getHeader("Lock-Token");

        if (id != null) {
            id = id.substring(id.indexOf(":") + 1, id.indexOf(">"));

        }

        return id;
    }

    /**
     * Checks if locks on resources at the given path exists and if so checks the If-Header to make
     * sure the If-Header corresponds to the locked resource. Returning true if no lock exists or
     * the If-Header is corresponding to the locked resource
     * 
     * @param req
     *            Servlet request
     * @param resp
     *            Servlet response
     * @param resourceLocks
     * @param path
     *            path to the resource
     * @param errorList
     *            List of error to be displayed
     * @return true if no lock on a resource with the given path exists or if the If-Header
     *         corresponds to the locked resource
     * @throws IOException
     * @throws LockFailedException
     */
    protected boolean checkLocks(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp,
            ResourceLocks resourceLocks, String path) throws IOException, LockException {

        LockedObject loByPath = resourceLocks.getLockedObjectByPath(transaction, path);
        if (loByPath != null) {

            if (loByPath.isShared())
                return true;

            // the resource is locked
            String[] lockTokens = getLockIdFromIfHeader(req);
            String lockToken = null;
            if (lockTokens != null)
                lockToken = lockTokens[0];
            else {
                return false;
            }
            if (lockToken != null) {
                LockedObject loByIf = resourceLocks.getLockedObjectByID(transaction, lockToken);
                if (loByIf == null) {
                    // no locked resource to the given lockToken
                    return false;
                }
                if (!loByIf.equals(loByPath)) {
                    loByIf = null;
                    return false;
                }
                loByIf = null;
            }

        }
        loByPath = null;
        return true;
    }

    /**
     * Send a multistatus element containing a complete error report to the client.
     * 
     * @param req
     *            Servlet request
     * @param resp
     *            Servlet response
     * @param errorList
     *            List of error to be displayed
     */
    protected void sendReport(HttpServletRequest req, HttpServletResponse resp, Map<String, Integer> errorList)
            throws IOException {
        resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);
        String absoluteUri = req.getRequestURI();

        try {
            XMLWriter XML = new XMLWriter();
            XML.setNameSpace("DAV:", "D");
            XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

            XML.addChildElement("D:multistatus");

            for (Entry<String, Integer> e : errorList.entrySet()) {
                String errorPath = e.getKey();
                int errorCode = e.getValue();

                XML.addChildElement("D:response");

                XML.addChildElement("D:href");
                String toAppend = null;
                if (absoluteUri.endsWith(errorPath)) {
                    toAppend = absoluteUri;

                } else if (absoluteUri.contains(errorPath)) {
                    int endIndex = absoluteUri.indexOf(errorPath) + errorPath.length();
                    toAppend = absoluteUri.substring(0, endIndex);
                }
                if (!toAppend.startsWith("/") && !toAppend.startsWith("http:")) {
                    toAppend = "/" + toAppend;
                }
                XML.setTextContent(errorPath);
                XML.closeElement();
                XML.addChildElement("D:status");
                XML.setTextContent("HTTP/1.1 " + errorCode + " " + CalDAVResponse.getStatusText(errorCode));
                XML.closeElement();

                XML.closeElement();
            }

            XML.closeElement();

            Writer writer = resp.getWriter();
            writer.write(XML.toString());
            writer.close();
        } catch (ParserConfigurationException e) {
            // nothing
        }
    }

    protected void sendPrivilegeError(HttpServletResponse response, String uri, String privilege) throws IOException {
        response.setStatus(CalDAVResponse.SC_FORBIDDEN);

        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("DAV:", "D");
        try {
            XMLWriter XML = new XMLWriter();
            XML.setNameSpace("DAV:", "D");
            XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

            XML.addChildElement("D:error");
            XML.addChildElement("D:need-privleges");

            XML.addChildElement("D:href");
            XML.setTextContent(uri);
            XML.closeElement();

            XML.addChildElement("D:privilege");
            XML.setTextContent(privilege);
            XML.closeElement();

            XML.closeElement();
            XML.closeElement();

            Writer writer = response.getWriter();
            writer.write(XML.toString());
            writer.close();
        } catch (ParserConfigurationException e) {
            // nothing
        }
    }
}
