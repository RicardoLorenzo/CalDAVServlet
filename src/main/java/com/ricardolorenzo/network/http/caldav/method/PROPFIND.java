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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;
import com.ricardolorenzo.icalendar.VEvent;
import com.ricardolorenzo.icalendar.VTodo;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.CalDAVMimeType;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.LockedObject;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVPrivilege;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVPrivilegeCollection;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.StoredObject;
import com.ricardolorenzo.network.http.caldav.store.VCalendarCache;
import com.ricardolorenzo.xml.XMLReader;
import com.ricardolorenzo.xml.XMLWriter;

/**
 * @author Ricardo Lorenzo
 * 
 */
public class PROPFIND extends CalDAVAbstractMethod {
    private static final int FIND_BY_PROPERTY = 0;
    private static final int FIND_ALL_PROP = 1;
    private static final int FIND_PROPERTY_NAMES = 2;

    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;
    private CalDAVResourceACL resource_acl;
    private CalDAVMimeType _mimeType;
    private int _depth;

    public PROPFIND(CalDAVStore store, ResourceLocksMap resLocks, CalDAVMimeType mimeType) {
        this._store = store;
        this._resource_locks = resLocks;
        this._mimeType = mimeType;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        String path = getCleanPath(getRelativePath(req));
        String tempLockOwner = "PROPFIND" + System.currentTimeMillis() + req.toString();
        this.resource_acl = this._store.getResourceACL(transaction, path);
        this._depth = getDepth(req);

        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, this._depth, TEMP_TIMEOUT, TEMPORARY)) {
            StoredObject so = null;
            try {
                this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(), "read");
                so = this._store.getStoredObject(transaction, path);
                if (so == null) {
                    resp.setContentType("text/xml; charset=UTF-8");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                    return;
                }

                List<String> properties = null;
                path = getCleanPath(getRelativePath(req));

                int propertyFindType = FIND_ALL_PROP;
                Node propNode = null;

                if (req.getContentLength() > 0) {
                    try {
                        Document document = XMLReader.getDocument(req.getInputStream());
                        Element rootElement = document.getDocumentElement();

                        propNode = XMLReader.firstSubElement(rootElement, "prop");
                        if (propNode != null) {
                            propertyFindType = FIND_BY_PROPERTY;
                        } else if (XMLReader.firstSubElement(rootElement, "propname") != null) {
                            propertyFindType = FIND_PROPERTY_NAMES;
                        } else if (XMLReader.firstSubElement(rootElement, "allprop") != null) {
                            propertyFindType = FIND_ALL_PROP;
                        }
                    } catch (IOException e) {
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (SAXException e) {
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }
                } else {
                    propertyFindType = FIND_ALL_PROP;
                }

                if (propertyFindType == FIND_BY_PROPERTY) {
                    propertyFindType = 0;
                    properties = XMLReader.getProperties(propNode);
                }

                resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);
                resp.setContentType("text/xml; charset=UTF-8");

                XMLWriter XML = new XMLWriter();
                XML.setNameSpace("DAV:", "D");
                XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");
                XML.setNameSpace("http://calendarserver.org/ns/", "CS");

                XML.addChildElement("D:multistatus");
                recursiveParseProperties(transaction, path, req, XML, propertyFindType, properties, this._depth,
                        this._mimeType.getMimeType(path));
                showCalendarItems(transaction, path, req, XML);
                XML.closeElement();

                byte[] content = XML.toString().getBytes("UTF-8");
                resp.setContentLength(content.length);
                OutputStream os = resp.getOutputStream();
                os.write(content);
                os.close();
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (IOException e) {
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (ParserConfigurationException e) {
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

    /**
     * goes recursive through all folders. used by propfind
     * 
     * @param currentPath
     *            the current path
     * @param req
     *            HttpServletRequest
     * @param XML
     * @param propertyFindType
     * @param properties
     * @param depth
     *            depth of the propfind
     * @throws IOException
     *             if an error in the underlying store occurs
     */
    private void recursiveParseProperties(CalDAVTransaction transaction, String currentPath, HttpServletRequest req,
            XMLWriter XML, int propertyFindType, List<String> properties, int depth, String mimeType)
            throws CalDAVException {

        parseProperties(transaction, req, XML, currentPath, propertyFindType, properties, mimeType);

        if (depth > 0) {
            String newPath = null;
            for (String name : this._store.getChildrenNames(transaction, currentPath)) {
                if (name.equals("calendar.ics")) {
                    continue;
                }
                newPath = currentPath;
                if (!(newPath.endsWith("/"))) {
                    newPath += "/";
                }
                newPath += name;
                recursiveParseProperties(transaction, newPath, req, XML, propertyFindType, properties, depth - 1,
                        mimeType);
            }
        }
    }

    /**
     * Show calendar items as files. used by propfind
     * 
     * @param transaction
     *            CalDAVTransaction
     * @param path
     *            the current path
     * @param req
     *            HttpServletRequest
     * @param XML
     * @throws IOException
     *             if an error in the underlying store occurs
     */
    private void showCalendarItems(CalDAVTransaction transaction, String path, HttpServletRequest req, XMLWriter XML)
            throws IOException {
        if (this._store.resourceExists(transaction, path)) {
            for (String name : this._store.getChildrenNames(transaction, path)) {
                if (name.equals("calendar.ics")) {
                    File _f = new File(this._store.getRootPath() + path + "/calendar.ics");
                    try {
                        VCalendar _vc = VCalendarCache.getVCalendar(_f);
                        String status = new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
                                + CalDAVResponse.getStatusText(CalDAVResponse.SC_OK));

                        for (VEvent _ve : _vc.getVevents()) {
                            XML.addChildElement("D:response");

                            XML.addChildElement("D:href");
                            XML.setTextContent("/caldav" + path + "/" + _ve.getUid() + ".ics");
                            XML.closeElement();

                            XML.addChildElement("D:getetag");
                            XML.setTextContent(getETag(_ve));
                            XML.closeElement();

                            /*
                             * if(_ve.hasLastModified()) { XML.addProperty("D:getlastmodified",
                             * LAST_MODIFIED_DATE_FORMAT.format(_ve.getLastModified().getTime())); }
                             */

                            XML.addChildElement("D:status");
                            XML.setTextContent(status);
                            XML.closeElement();

                            XML.closeElement();
                        }

                        for (VTodo _vt : _vc.getVtodos()) {
                            XML.addChildElement("D:response");

                            XML.addChildElement("D:href");
                            XML.setTextContent("/caldav" + path + "/" + _vt.getUid() + ".ics");
                            XML.closeElement();

                            XML.addChildElement("D:getetag");
                            XML.setTextContent(getETag(_vt));
                            XML.closeElement();

                            /*
                             * if(_vt.hasLastModified()) { XML.addProperty("D:getlastmodified",
                             * LAST_MODIFIED_DATE_FORMAT.format(_vt.getLastModified().getTime())); }
                             */

                            XML.addChildElement("D:status");
                            XML.setTextContent(status);
                            XML.closeElement();

                            XML.closeElement();
                        }
                    } catch (IOException e) {
                        // nothing
                    } catch (VCalendarException e) {
                        // nothing
                    }
                    break;
                }
            }
        }
    }

    /**
     * Propfind helper method.
     * 
     * @param req
     *            The servlet request
     * @param XML
     *            XML response to the Propfind request
     * @param path
     *            Path of the current resource
     * @param type
     *            Propfind type
     * @param propertiesVector
     *            If the propfind type is find properties by name, then this Vector contains those
     *            properties
     */
    private void parseProperties(CalDAVTransaction transaction, HttpServletRequest req, XMLWriter XML, String path,
            int type, List<String> properties, String mimeType) throws CalDAVException {
        CalDAVResourceACL ACLResource = this._store.getResourceACL(transaction, path);
        StoredObject so = this._store.getStoredObject(transaction, path);

        boolean isFolder = so.isFolder();
        String creationdate = CREATION_DATE_FORMAT.format(so.getCreationDate());
        String lastModified = LAST_MODIFIED_DATE_FORMAT.format(so.getLastModified());
        String resourceLength = String.valueOf(so.getResourceLength());

        XML.addChildElement("D:response");
        String status = new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
                + CalDAVResponse.getStatusText(CalDAVResponse.SC_OK));

        String href = req.getContextPath();
        try {
            XML.addChildElement("D:href");

            if (req.getServletPath() != null) {
                String servletPath = req.getServletPath();
                if ((href.endsWith("/")) && (servletPath.startsWith("/"))) {
                    href += servletPath.substring(1);
                } else {
                    href += servletPath;
                }
            }
            if ((href.endsWith("/")) && (path.startsWith("/"))) {
                href += path.substring(1);
            } else {
                href += path;
            }
            if ((isFolder) && (!href.endsWith("/"))) {
                href += "/";
            }

            XML.setTextContent(rewriteUrl(href));
        } catch (UnsupportedEncodingException e) {
            throw new CalDAVException("encoding exception [" + href + "]");
        }
        XML.closeElement();

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            resourceName = resourceName.substring(lastSlash + 1);
        }

        switch (type) {
            case FIND_ALL_PROP:
                XML.addChildElement("D:propstat");
                XML.addChildElement("D:prop");

                XML.addProperty("D:creationdate", creationdate);
                XML.addChildElement("D:displayname");
                XML.setDataContent(resourceName);
                XML.closeElement();
                if (!isFolder) {
                    XML.addProperty("D:getlastmodified", lastModified);
                    XML.addProperty("D:getcontentlength", resourceLength);
                    String contentType = mimeType;
                    if (contentType != null) {
                        XML.addProperty("D:getcontenttype", contentType);
                    }
                    XML.addProperty("D:getetag", getETag(so));
                    XML.addProperty("D:resourcetype");
                } else {
                    XML.addChildElement("D:resourcetype");
                    XML.addProperty("D:collection");
                    XML.closeElement();
                }

                writeSupportedLockElements(transaction, XML, path);
                writeLockDiscoveryElements(transaction, XML, path);

                StoredObject _so = this._store.getStoredObject(transaction, path + "/calendar.ics");
                if (_so != null) {
                    writeCalendarElements(transaction, XML);
                }

                XML.addProperty("D:source", "");
                XML.closeElement();

                XML.addChildElement("D:status");
                XML.setTextContent(status);
                XML.closeElement();

                XML.closeElement();
                break;
            case FIND_PROPERTY_NAMES:
                XML.addChildElement("D:propstat");
                XML.addChildElement("D:prop");

                XML.addProperty("D:creationdate");
                XML.addProperty("D:displayname");
                if (!isFolder) {
                    XML.addProperty("D:getcontentlanguage");
                    XML.addProperty("D:getcontentlength");
                    XML.addProperty("D:getcontenttype");
                    XML.addProperty("D:getetag");
                    XML.addProperty("D:getlastmodified");
                }
                XML.addProperty("D:resourcetype");
                XML.addProperty("D:supportedlock");
                XML.addProperty("D:source");

                XML.closeElement();

                XML.addChildElement("D:status");
                XML.setTextContent(status);
                XML.closeElement();

                XML.closeElement();
                break;
            case FIND_BY_PROPERTY:
                CalDAVPrivilegeCollection collection;
                List<String> propertiesNotFound = new ArrayList<String>();

                XML.addChildElement("D:propstat");
                XML.addChildElement("D:prop");

                for (String property : properties) {
                    if (property.contains(":")) {
                        property = property.substring(property.lastIndexOf(":") + 1);
                    }

                    if (property.equals("creationdate")) {
                        XML.addProperty("D:creationdate", creationdate);
                    } else if (property.equals("displayname")) {
                        XML.addChildElement("D:displayname");
                        XML.setDataContent(resourceName);
                        XML.closeElement();
                    } else if (property.equals("getcontentlanguage")) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            XML.addProperty("D:getcontentlanguage");
                        }
                    } else if (property.equals("getcontentlength")) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            XML.addProperty("D:getcontentlength", resourceLength);
                        }
                    } else if (property.equals("getcontenttype")) {
                        if (isFolder) {
                            propertiesNotFound.add("D:getcontenttype");
                        } else {
                            XML.addProperty("D:getcontenttype", mimeType);
                        }
                    } else if (property.equals("getetag")) {
                        if (isFolder || so.isNullResource()) {
                            List<String> childs = Arrays.asList(this._store.getChildrenNames(transaction, path));
                            if (childs.contains("calendar.ics")) {
                                if (!path.endsWith("/")) {
                                    path = path.concat("/");
                                }
                                StoredObject soc = this._store.getStoredObject(transaction, path + "calendar.ics");
                                XML.addProperty("D:getetag", getETag(soc));
                            } else {
                                propertiesNotFound.add("D:getetag");
                            }
                        } else {
                            XML.addProperty("D:getetag", getETag(so));
                        }
                    } else if (property.equals("getctag")) {
                        if (isFolder || so.isNullResource()) {
                            List<String> childs = Arrays.asList(this._store.getChildrenNames(transaction, path));
                            if (childs.contains("calendar.ics")) {
                                if (!path.endsWith("/")) {
                                    path = path.concat("/");
                                }
                                StoredObject soc = this._store.getStoredObject(transaction, path + "calendar.ics");
                                XML.addProperty("CS:getctag", getCTag(soc));
                            } else {
                                propertiesNotFound.add("CS:getctag");
                            }
                        } else {
                            propertiesNotFound.add("CS:getctag");
                        }
                    } else if (property.equals("getlastmodified")) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            XML.addProperty("D:getlastmodified", lastModified);
                        }
                    } else if (property.equals("resourcetype")) {
                        if (isFolder) {
                            XML.addChildElement("D:resourcetype");
                            XML.addProperty("D:collection");
                            /*
                             * TODO Identify when is a calendar collection
                             */
                            _so = this._store.getStoredObject(transaction, path + "/calendar.ics");
                            if (_so != null) {
                                XML.addProperty("C:calendar");
                            }

                            XML.closeElement();
                        } else {
                            XML.addProperty("D:resourcetype");
                        }
                    } else if (property.equals("source")) {
                        XML.addProperty("D:source", "");
                    } else if (property.equals("supportedlock")) {
                        writeSupportedLockElements(transaction, XML, path);
                    } else if (property.equals("lockdiscovery")) {
                        writeLockDiscoveryElements(transaction, XML, path);
                    } else if (property.equals("owner")) {
                        collection = this.resource_acl.getPrivilegeCollection();

                        XML.addChildElement("D:owner");
                        XML.addChildElement("D:href");
                        XML.setTextContent("http://" + req.getServerName() + "/acl/users/"
                                + collection.getOwner().getName());
                        XML.closeElement();
                        XML.closeElement();
                    } else if (property.equals("supported-privilege-set")) {
                        XML.addChildElement("D:supported-privilege-set");

                        Map<String, String> supportedPrivileges = this.resource_acl.getSupportedPrivilegeSet();

                        XML.addChildElement("D:supported-privilege");
                        XML.addChildElement("D:privilege");
                        XML.addProperty("D:all");
                        XML.closeElement();
                        XML.addProperty("D:abstract");
                        XML.addChildElement("D:description");
                        XML.setTextContent(supportedPrivileges.get("all"));
                        XML.closeElement();

                        for (String p : supportedPrivileges.keySet()) {
                            if ("all".equals(p)) {
                                continue;
                            }

                            XML.addChildElement("D:supported-privilege");

                            XML.addChildElement("D:privilege");
                            XML.addProperty("D:" + p);
                            XML.closeElement();

                            XML.addProperty("D:abstract");

                            XML.addChildElement("D:description");
                            XML.setTextContent(supportedPrivileges.get(p));
                            XML.closeElement();

                            XML.closeElement();
                        }

                        XML.closeElement();
                    } else if (property.equals("current-user-privilege-set")) {
                        collection = ACLResource.getPrivilegeCollection();
                        CalDAVPrivilege privilege = collection.getPrincipalPrivilege(transaction.getPrincipal());

                        XML.addChildElement("D:current-user-privilege-set");

                        for (String p : privilege.getGrantedPrivileges()) {
                            XML.addChildElement("D:privilege");
                            XML.addProperty("D:" + p);
                            XML.closeElement();
                        }

                        XML.closeElement();
                    } else if (property.equals("acl")) {
                        collection = this.resource_acl.getPrivilegeCollection();

                        XML.addChildElement("D:acl");

                        XML.addChildElement("D:ace");

                        XML.addChildElement("D:principal");
                        XML.setTextContent("http://" + req.getServerName() + "/acl/users/"
                                + collection.getOwner().getName());
                        XML.closeElement();

                        XML.addChildElement("D:grant");
                        XML.addChildElement("D:privilege");
                        XML.addProperty("D:all");
                        XML.closeElement();
                        XML.closeElement();

                        XML.closeElement();

                        for (CalDAVPrivilege p : collection.getAllPrivileges()) {
                            XML.addChildElement("D:ace");

                            XML.addChildElement("D:principal");
                            XML.setTextContent("http://" + req.getServerName() + "/acl/users/" + p.getPrincipalName());
                            XML.closeElement();

                            if (!p.getGrantedPrivileges().isEmpty()) {
                                XML.addChildElement("D:grant");
                                for (String pn : p.getGrantedPrivileges()) {
                                    XML.addChildElement("D:privilege");
                                    XML.addProperty("D:" + pn);
                                    XML.closeElement();
                                }
                                XML.closeElement();
                            }

                            if (!p.getDeniedPrivileges().isEmpty()) {
                                XML.addChildElement("D:deny");
                                for (String pn : p.getDeniedPrivileges()) {
                                    XML.addChildElement("D:privilege");
                                    XML.addProperty("D:" + pn);
                                    XML.closeElement();
                                }
                                XML.closeElement();
                            }

                            XML.closeElement();
                        }

                        XML.closeElement();
                    } else if (property.equals("acl-restrictions")) {
                        XML.addChildElement("D:acl-restrictions");

                        XML.addProperty("D:no-invert");
                        XML.addChildElement("D:required-principal");
                        XML.addProperty("D:all");
                        XML.closeElement();

                        XML.closeElement();
                    } else if (property.equals("principal-collection-set")) {
                        XML.addChildElement("D:principal-collection-set");

                        XML.addChildElement("D:href");
                        XML.setTextContent("http://" + req.getServerName() + "/acl/users/");
                        XML.closeElement();

                        XML.addChildElement("D:href");
                        XML.setTextContent("http://" + req.getServerName() + "/acl/groups/");
                        XML.closeElement();

                        XML.closeElement();
                    } else if (property.equals("supported-calendar-component-set")) {
                        List<String> childs = Arrays.asList(this._store.getChildrenNames(transaction, path));
                        if (childs.contains("calendar.ics")) {
                            XML.addChildElement("C:supported-calendar-component-set");

                            XML.addChildElement("C:comp");
                            XML.addAttribute("name", "VTIMEZONE");
                            XML.closeElement();

                            XML.addChildElement("C:comp");
                            XML.addAttribute("name", "VEVENT");
                            XML.closeElement();

                            XML.addChildElement("C:comp");
                            XML.addAttribute("name", "VTODO");
                            XML.closeElement();

                            XML.addChildElement("C:comp");
                            XML.addAttribute("name", "VFREEBUSY");
                            XML.closeElement();

                            XML.closeElement();
                        } else {
                            propertiesNotFound.add("C:supported-calendar-component-set");
                        }
                    } else if (property.equals("supported-calendar-data")) {
                        List<String> childs = Arrays.asList(this._store.getChildrenNames(transaction, path));
                        if (childs.contains("calendar.ics")) {
                            XML.addChildElement("C:supported-calendar-component-set");
                            XML.addChildElement("C:calendar-data");
                            XML.addAttribute("version", "2.0");
                            XML.addAttribute("content-type", "text/calendar");
                            XML.closeElement();
                            XML.closeElement();
                        } else {
                            propertiesNotFound.add("C:supported-calendar-data");
                        }
                    } else {
                        propertiesNotFound.add(property);
                    }
                }

                XML.closeElement();
                XML.addChildElement("D:status");
                XML.setTextContent(status);
                XML.closeElement();
                XML.closeElement();

                if (!propertiesNotFound.isEmpty()) {
                    status = new String("HTTP/1.1 " + CalDAVResponse.SC_NOT_FOUND + " "
                            + CalDAVResponse.getStatusText(CalDAVResponse.SC_NOT_FOUND));

                    XML.addChildElement("D:propstat");
                    XML.addChildElement("D:prop");

                    for (String property : propertiesNotFound) {
                        XML.addProperty(property);
                    }

                    XML.closeElement();
                    XML.addChildElement("D:status");
                    XML.setTextContent(status);
                    XML.closeElement();

                    XML.closeElement();
                }
                break;
        }
        XML.closeElement();
        so = null;
    }

    private void writeSupportedLockElements(CalDAVTransaction transaction, XMLWriter XML, String path) {
        LockedObject lo = this._resource_locks.getLockedObjectByPath(transaction, path);
        XML.addChildElement("D:supportedlock");

        if (lo == null) {
            // both locks (shared/exclusive) can be granted
            XML.addChildElement("D:lockentry");

            XML.addChildElement("D:lockscope");
            XML.addProperty("D:exclusive");
            XML.closeElement();

            XML.addChildElement("D:locktype");
            XML.addProperty("D:write");
            XML.closeElement();

            XML.closeElement();

            XML.addChildElement("D:lockentry");

            XML.addChildElement("D:lockscope");
            XML.addProperty("D:shared");
            XML.closeElement();

            XML.addChildElement("D:locktype");
            XML.addProperty("D:write");
            XML.closeElement();

            XML.closeElement();
        } else {
            // LockObject exists, checking lock state
            // if an exclusive lock exists, no further lock is possible
            if (lo.isShared()) {
                XML.addChildElement("D:lockentry");

                XML.addChildElement("D:lockscope");
                XML.addProperty("D:shared");
                XML.closeElement();

                XML.addChildElement("D:locktype");
                XML.addProperty("D:" + lo.getType());
                XML.closeElement();

                XML.closeElement();
            }
        }

        XML.closeElement();
        lo = null;
    }

    private void writeLockDiscoveryElements(CalDAVTransaction transaction, XMLWriter XML, String path) {

        LockedObject lo = this._resource_locks.getLockedObjectByPath(transaction, path);

        if (lo != null && !lo.hasExpired()) {
            XML.addChildElement("D:lockdiscovery");
            XML.addChildElement("D:activelock");

            XML.addChildElement("D:locktype");
            XML.addProperty("D:" + lo.getType());
            XML.closeElement();

            XML.addChildElement("D:lockscope");
            if (lo.isExclusive()) {
                XML.addProperty("D:exclusive");
            } else {
                XML.addProperty("D:shared");
            }
            XML.closeElement();

            XML.addChildElement("D:depth");
            if (this._depth == INFINITY) {
                XML.setTextContent("Infinity");
            } else {
                XML.setTextContent(String.valueOf(this._depth));
            }
            XML.closeElement();

            String[] owners = lo.getOwner();
            if (owners != null) {
                for (int i = 0; i < owners.length; i++) {
                    XML.addChildElement("D:owner");
                    XML.addChildElement("D:href");
                    XML.setTextContent(owners[i]);
                    XML.closeElement();
                    XML.closeElement();
                }
            } else {
                XML.addProperty("D:owner");
            }

            int timeout = (int) (lo.getTimeoutMillis() / 1000);
            String timeoutStr = new Integer(timeout).toString();
            XML.addChildElement("D:timeout");
            XML.setTextContent("Second-" + timeoutStr);
            XML.closeElement();

            String lockToken = lo.getID();

            XML.addChildElement("D:locktoken");
            XML.addChildElement("D:href");
            XML.setTextContent("opaquelocktoken:" + lockToken);
            XML.closeElement();
            XML.closeElement();

            XML.closeElement();
            XML.closeElement();
        } else {
            XML.addProperty("D:lockdiscovery");
        }
        lo = null;
    }

    private void writeCalendarElements(CalDAVTransaction transaction, XMLWriter XML) {
        XML.addChildElement("C:calendar-description");
        XML.setTextContent("WBSVia-Calendar");
        XML.closeElement();

        XML.addChildElement("C:supported-calendar-component-set");

        XML.addChildElement("C:comp");
        XML.addAttribute("name", "VTIMEZONE");
        XML.closeElement();

        XML.addChildElement("C:comp");
        XML.addAttribute("name", "VEVENT");
        XML.closeElement();

        XML.addChildElement("C:comp");
        XML.addAttribute("name", "VTODO");
        XML.closeElement();

        XML.addChildElement("comp");
        XML.addAttribute("name", "VFREEBUSY");
        XML.closeElement();

        XML.closeElement();

        XML.addChildElement("C:supported-calendar-component-set");

        XML.addChildElement("C:calendar-data");
        XML.addAttribute("version", "2.0");
        XML.addAttribute("content-type", "text/calendar");
        XML.closeElement();

        XML.closeElement();
    }
}
