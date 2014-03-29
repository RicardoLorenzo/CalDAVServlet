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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ricardolorenzo.file.lock.FileLockException;
import com.ricardolorenzo.file.xml.XMLReader;
import com.ricardolorenzo.file.xml.XMLWriter;
import com.ricardolorenzo.icalendar.DateTime;
import com.ricardolorenzo.icalendar.Period;
import com.ricardolorenzo.icalendar.Person;
import com.ricardolorenzo.icalendar.VAction;
import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;
import com.ricardolorenzo.icalendar.VEvent;
import com.ricardolorenzo.icalendar.VFreeBusy;
import com.ricardolorenzo.icalendar.VTimeZone;
import com.ricardolorenzo.icalendar.VTodo;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVResponse;
import com.ricardolorenzo.network.http.caldav.locking.LockException;
import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;
import com.ricardolorenzo.network.http.caldav.store.VCalendarCache;

/**
 * @author Ricardo Lorenzo
 * 
 */
public class REPORT extends CalDAVAbstractMethod {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVStore _store;
    private ResourceLocksMap _resource_locks;
    private CalDAVResourceACL resource_acl;
    private boolean expand = false;

    public REPORT(CalDAVStore store, ResourceLocksMap resLocks) {
        this._store = store;
        this._resource_locks = resLocks;
    }

    public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, LockException {
        String path = getCleanPath(getRelativePath(req));

        String tempLockOwner = "REPORT" + System.currentTimeMillis() + req.toString();
        if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY)) {
            try {
                if (req.getContentLength() > 0) {
                    resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);
                    resp.setContentType("application/xml; charset=UTF-8");

                    XMLWriter XML = new XMLWriter();
                    XML.setNameSpace("DAV:", "D");
                    XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");
                    XML.setNameSpace("http://calendarserver.org/ns/", "CS");

                    XML.addChildElement("D:multistatus");

                    try {
                        Document document = XMLReader.getDocument(req.getInputStream());
                        Element rootElement = document.getDocumentElement();

                        if ("calendar-query".equals(rootElement.getLocalName())) {
                            Calendar start = null, end = null;
                            Node filter = XMLReader.findFirstSubElement(rootElement, "calendar-data");
                            if (filter != null) {
                                if (!XMLReader.getChildElements(filter, "expand").isEmpty()) {
                                    this.expand = true;
                                }
                            }
                            filter = XMLReader.findFirstSubElement(rootElement, "filter");
                            if (filter != null) {
                                for (Node n : XMLReader.getAllChildElements(filter, "comp-filter")) {
                                    String type = ((Element) n).getAttribute("name");
                                    if (type == null
                                            || (!type.toUpperCase().equals("VEVENT") && !type.toUpperCase().equals(
                                                    "VTODO"))) {
                                        continue;
                                    }
                                    if (n.hasChildNodes()) {
                                        for (Node nn : XMLReader.getChildElements(n)) {
                                            Element e = (Element) nn;
                                            if ("time-range".equals(nn.getLocalName())) {
                                                Period p = null;
                                                if (e.hasAttribute("start") && e.hasAttribute("end")) {
                                                    start = DateTime.getCalendarFromString(null,
                                                            e.getAttribute("start"));
                                                    end = DateTime.getCalendarFromString(null, e.getAttribute("end"));
                                                    p = new Period(start, end);
                                                }
                                                processPeriodCalendarActions(transaction, path, p, XML, type);
                                            } else if ("prop-filter".equals(nn.getLocalName())) {
                                                String subtype = ((Element) nn).getAttribute("name");
                                                for (Node nnn : XMLReader.getChildElements(nn)) {
                                                    if ("text-match".equals(nnn.getLocalName())) {
                                                        processCalendarActions(transaction, path, nnn.getTextContent(),
                                                                XML, type, subtype);
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        processCalendarActions(transaction, path, null, XML, type);
                                    }
                                }
                            }
                        } else if ("calendar-multiget".equals(rootElement.getLocalName())) {
                            for (Node calendar : XMLReader.getChildElements(rootElement, "href")) {
                                processGet(transaction, calendar.getTextContent(), XML);
                            }
                        }
                    } catch (IOException e) {
                    	logger.error("report", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (SAXException e) {
                    	logger.error("report", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (VCalendarException e) {
                    	logger.error("report", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    } catch (FileLockException e) {
                    	logger.error("report", e);
                        resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }

                    XML.closeElement();
                    XML.write(resp.getOutputStream());
                }
            } catch (AccessDeniedException e) {
                resp.sendError(CalDAVResponse.SC_FORBIDDEN);
            } catch (IOException e) {
            	logger.error("report", e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (ParserConfigurationException e) {
            	logger.error("report", e);
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

    private void processPeriodCalendarActions(CalDAVTransaction transaction, String path, Period p, XMLWriter XML,
            String type) throws VCalendarException, IOException, FileLockException {
        if ("VFREEBUSY".equals(type)) {
            String[] names = this._store.getChildrenNames(transaction, path);
            if (names != null) {
                for (String name : names) {
                    processCalendarActions(transaction, path + (path.endsWith("/") ? "" : "/") + name, p, XML, type);
                }
            } else {
                processCalendarActions(transaction, path, p, XML, type);
            }
        } else {
            processCalendarActions(transaction, path, p, XML, type);
        }
    }

    private void processCalendarActions(CalDAVTransaction transaction, String path, Period p, XMLWriter XML, String type)
            throws VCalendarException, IOException, FileLockException {
        String href = path + "/calendar.ics";
        File _f = new File(this._store.getRootPath() + href);
        if (_f.exists()) {
            String eTag = getETag(this._store.getStoredObject(transaction, href));
            String cTag = getCTag(this._store.getStoredObject(transaction, href));
            if (!"VFREEBUSY".equals(type)) {
                try {
                    this.resource_acl = this._store.getResourceACL(transaction, href);
                    this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(),
                            "read");
                } catch (AccessDeniedException e) {
                    return;
                }
            }

            VCalendar vc = VCalendarCache.getVCalendar(_f);
            if ("VEVENT".equals(type)) {
                if (p == null) {
                    for (VEvent ve : vc.getVevents()) {
                        if (ve == null) {
                            continue;
                        }
                        printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
                    }
                } else {
                    if (this.expand) {
                        for (VEvent ve : vc.getRecurrentVevents(p)) {
                            if (ve == null) {
                                continue;
                            }
                            printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
                        }
                    } else {
                        for (VEvent ve : vc.getVevents(p)) {
                            if (ve == null) {
                                continue;
                            }
                            printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
                        }
                    }
                }
            } else if ("VTODO".equals(type)) {
                if (p == null) {
                    for (VTodo vt : vc.getVtodos()) {
                        if (vt == null) {
                            continue;
                        }
                        printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
                    }
                } else {
                    if (this.expand) {
                        for (VTodo vt : vc.getRecurrentVtodos(p)) {
                            if (vt == null) {
                                continue;
                            }
                            printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
                        }
                    } else {
                        for (VTodo vt : vc.getVtodos(p)) {
                            if (vt == null) {
                                continue;
                            }
                            printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
                        }
                    }
                }
            } else if ("VFREEBUSY".equals(type)) {
                if (p != null) {
                    VFreeBusy _vfb = vc.getVFreeBusy(p);
                    _vfb.setOrganizer("", new Person("CN=" + transaction.getPrincipal().getName(), Person.ORGANIZER));
                    printVFreeBusy(XML, path, eTag, vc.getTimeZone(), _vfb);
                }
            }
        }
    }

    private void processCalendarActions(CalDAVTransaction transaction, String path, String match, XMLWriter XML,
            String type, String subtype) {
        String href = path + "/calendar.ics";
        try {
            this.resource_acl = this._store.getResourceACL(transaction, href);
            this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(), "read");
        } catch (AccessDeniedException e) {
            return;
        }

        File _f = new File(this._store.getRootPath() + href);
        if (_f.exists()) {
            String eTag = getETag(this._store.getStoredObject(transaction, href));
            String cTag = getCTag(this._store.getStoredObject(transaction, href));
            try {
                VCalendar vc = VCalendarCache.getVCalendar(_f);
                if ("VEVENT".equals(type)) {
                    if ("UID".equals(subtype)) {
                        VEvent ve = vc.getVevent(match);
                        if (ve != null) {
                            printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
                        }
                    }
                } else if ("VTODO".equals(type)) {
                    if ("UID".equals(subtype)) {
                        VTodo vt = vc.getVtodo(match);
                        if (vt != null) {
                            printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
                        }
                    }
                }
            } catch (IOException e) {
            	logger.error("report", e);
                // nothing
            } catch (VCalendarException e) {
            	logger.error("report", e);
                // nothing
            } catch (FileLockException e) {
            	logger.error("report", e);
                // nothing
            }
        }
    }

    private void processGet(CalDAVTransaction transaction, String path, XMLWriter XML) throws IOException {
        String href = path.substring(0, path.lastIndexOf("/"));
        while (href.endsWith(".ics")) {
            href = href.substring(0, href.lastIndexOf("/"));
        }
        if (href.startsWith("/caldav")) {
            href = href.substring(7);
        }
        href = href.concat("/calendar.ics");
        try {
            this.resource_acl = this._store.getResourceACL(transaction, href);
            this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(), "read");
        } catch (AccessDeniedException e) {
            return;
        }

        File _f = new File(this._store.getRootPath() + href);
        if (_f.exists()) {
            String eTag = getETag(this._store.getStoredObject(transaction, href));
            String cTag = getCTag(this._store.getStoredObject(transaction, href));
            String uid = path.substring(path.lastIndexOf("/") + 1);
            if (uid.endsWith(".ics")) {
                uid = uid.substring(0, uid.length() - 4);
            }

            try {
                VCalendar vc = VCalendarCache.getVCalendar(_f);
                try {
                    VEvent ve = vc.getVevent(uid);
                    if (ve != null) {
                        printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
                    }
                } catch (VCalendarException e) {
                    VTodo vt = vc.getVtodo(uid);
                    if (vt != null) {
                        printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
                    }
                }
            } catch (VCalendarException e) {
            	logger.error("report", e);
                // nothing
            } catch (FileLockException e) {
            	logger.error("report", e);
                // nothing
            }
        }
    }

    private static void printVAction(XMLWriter XML, String path, String eTag, String cTag, VTimeZone tz, VAction va)
            throws VCalendarException {
        if (!path.endsWith("/")) {
            path = path.concat("/");
        }

        XML.addChildElement("D:response");

        XML.addChildElement("D:href");
        XML.setTextContent(path + va.getUid() + ".ics");
        XML.closeElement();

        XML.addChildElement("D:propstat");
        XML.addChildElement("D:prop");

        XML.addChildElement("D:getetag");
        XML.setTextContent(eTag);
        XML.closeElement();

        XML.addChildElement("CS:getctag");
        XML.setTextContent(cTag);
        XML.closeElement();

        XML.addChildElement("C:calendar-data");

        VCalendar vcal = new VCalendar();
        vcal.setTimeZone(tz);
        if (va instanceof VEvent) {
            vcal.addVevent((VEvent) va);
        } else if (va instanceof VTodo) {
            vcal.addVtodo((VTodo) va);
        }
        XML.setDataContent(vcal.toString());

        XML.closeElement();

        XML.addChildElement("D:status");
        XML.setTextContent(new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
                + CalDAVResponse.getStatusText(CalDAVResponse.SC_OK)));
        XML.closeElement();

        XML.closeElement();

        XML.closeElement();

        XML.closeElement();
    }

    private static void printVFreeBusy(XMLWriter XML, String path, String eTag, VTimeZone tz, VFreeBusy vfb) {
        if (!path.endsWith("/")) {
            path = path.concat("/");
        }

        XML.addChildElement("D:response");

        XML.addChildElement("D:href");
        XML.setTextContent(path + "calendar.ics");
        XML.closeElement();

        XML.addChildElement("D:propstat");
        XML.addChildElement("D:prop");

        XML.addChildElement("D:getetag");
        XML.setTextContent(eTag);
        XML.closeElement();

        XML.addChildElement("C:calendar-data");

        StringBuilder _sb = new StringBuilder();
        _sb.append("BEGIN:VCALENDAR\n");
        _sb.append("VERSION:" + VCalendar.version + "\n");
        _sb.append("PRODID:" + VCalendar.prodid + "\n");
        if (tz != null) {
            _sb.append(tz.toString());
        }
        _sb.append(vfb.toString());
        _sb.append("END:VCALENDAR\n");

        XML.setDataContent(_sb.toString());

        XML.closeElement();

        XML.addChildElement("D:status");
        XML.setTextContent(new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
                + CalDAVResponse.getStatusText(CalDAVResponse.SC_OK)));
        XML.closeElement();

        XML.closeElement();

        XML.closeElement();

        XML.closeElement();
    }
}
