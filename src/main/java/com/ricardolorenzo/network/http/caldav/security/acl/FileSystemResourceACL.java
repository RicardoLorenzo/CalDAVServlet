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
package com.ricardolorenzo.network.http.caldav.security.acl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.file.xml.db.XMLAttribute;
import com.ricardolorenzo.file.xml.db.XMLDB;
import com.ricardolorenzo.file.xml.db.XMLDBException;
import com.ricardolorenzo.file.xml.db.XMLObject;
import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.security.CalDAVPrincipal;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class FileSystemResourceACL implements CalDAVResourceACL {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private CalDAVPrivilegeCollection privileges;
    private CalDAVStore store;
    private String path;
    private File _xml_file;
    private XMLObject resourceXMLObject;

    public FileSystemResourceACL(CalDAVStore store, CalDAVTransaction transaction, String path) throws CalDAVException {
        this.store = store;
        if (path == null || path.isEmpty()) {
            throw new CalDAVException("invalid resource path");
        }

        this.path = path;
        File _f = new File(this.store.getRootPath() + this.path);
        if (_f.exists()) {
            if (_f.isFile()) {
                _f = _f.getParentFile();
            }

            if (_f == null || !_f.isDirectory()) {
                throw new CalDAVException("can not determine the filesystem context");
            }

            this._xml_file = new File(_f.getAbsolutePath() + File.separator + ".acl.xml");
            if (!this._xml_file.exists()) {
                this.privileges = new CalDAVPrivilegeCollection(transaction.getPrincipal());
                storePrivilegeCollection(transaction);
            } else {
                boolean newResource = true;
                try {
                    XMLDB _db = new XMLDB(this._xml_file);
                    this.privileges = new CalDAVPrivilegeCollection();

                    for (XMLObject resource : _db.getObjects()) {
                        if (!resource.hasAttribute("path")) {
                            continue;
                        } else if (!this.path.equals(resource.getAttribute("path").getValue())) {
                            continue;
                        }

                        if (resource.hasAttribute("principal")) {
                            this.privileges
                                    .setOwner(new CalDAVPrincipal(resource.getAttribute("principal").getValue()));
                        }

                        newResource = false;
                        this.resourceXMLObject = resource;
                        for (XMLObject o : resource.getObjects()) {
                            CalDAVPrivilege privilege = null;
                            if (o.hasAttribute("principal")) {
                                privilege = new CalDAVPrivilege(new CalDAVPrincipal(o.getAttribute("principal")
                                        .getValue()));
                            } else {
                                privilege = new CalDAVPrivilege();
                                privilege.setPrincipal(new CalDAVPrincipal("all"));
                            }

                            for (String pName : CalDAVPrivilege.getSupportedPrivileges().keySet()) {
                                if (o.hasAttribute(pName)) {
                                    if ("grant".equals(o.getAttribute(pName).getValue())) {
                                        privilege.setGrantPrivilege(pName);
                                    } else {
                                        privilege.setDenyPrivilege(pName);
                                    }
                                }
                            }

                            this.privileges.setPrivilege(privilege);
                        }
                    }

                    if (newResource) {
                        this.privileges.setOwner(transaction.getPrincipal());
                        storePrivilegeCollection(transaction);
                    }
                } catch (XMLDBException e) {
                    throw new CalDAVException(e.getMessage());
                }
            }
        } else {
            _f = _f.getParentFile();
            this._xml_file = new File(_f.getAbsolutePath() + File.separator + ".acl.xml");
            this.privileges = new CalDAVPrivilegeCollection(transaction.getPrincipal());
        }
    }

    public CalDAVPrivilegeCollection getPrivilegeCollection() {
        return this.privileges;
    }

    public List<String> getPrincipalCollectionSet() {
        return new ArrayList<String>();
    }

    public Map<String, String> getSupportedPrivilegeSet() {
        return CalDAVPrivilege.getSupportedPrivileges();
    }

    public void setPrivilegeCollection(CalDAVTransaction transaction, CalDAVPrivilegeCollection collection)
            throws ACLException, AccessDeniedException {
        this.privileges = collection;
        storePrivilegeCollection(transaction);
    }

    public void removeCollection(CalDAVTransaction transaction) throws NullPointerException, CalDAVException {
        this.privileges.checkPrincipalPrivilege(transaction.getPrincipal(), "write");
        removePrivilegeCollection();
    }

    private void storePrivilegeCollection(CalDAVTransaction transaction) {
        try {
            XMLDB _db = new XMLDB(this._xml_file);
            List<XMLObject> objects = new ArrayList<XMLObject>();
            if (this.resourceXMLObject == null) {
                this.resourceXMLObject = _db.createXMLObject();
                XMLAttribute a = new XMLAttribute("path");
                a.setValue(this.path);
                this.resourceXMLObject.addAttribute(a);
            }

            if (!this.resourceXMLObject.hasAttribute("principal")) {
                if (this.privileges.getOwner() == null) {
                    this.privileges.setOwner(transaction.getPrincipal());
                }

                XMLAttribute a = new XMLAttribute("principal");
                a.setValue(this.privileges.getOwner().getName());
                this.resourceXMLObject.addAttribute(a);
            }

            for (CalDAVPrivilege privilege : this.privileges.getAllPrivileges()) {
                XMLObject o = _db.createXMLObject();
                XMLAttribute a = new XMLAttribute("principal");
                a.setValue(privilege.getPrincipalName());
                o.addAttribute(a);
                for (String p : privilege.getGrantedPrivileges()) {
                    a = new XMLAttribute(p);
                    a.setValue("grant");
                    o.addAttribute(a);
                }
                for (String p : privilege.getDeniedPrivileges()) {
                    a = new XMLAttribute(p);
                    a.setValue("deny");
                    o.addAttribute(a);
                }
                objects.add(o);
            }

            this.resourceXMLObject.setObjects(objects);
            _db.updateObject(this.resourceXMLObject);
            _db.store();
        } catch (XMLDBException e) {
            throw new CalDAVException(e.getMessage());
        }
    }

    private void removePrivilegeCollection() throws NullPointerException, CalDAVException {
        try {
            XMLDB _db = new XMLDB(this._xml_file);
            if (this.resourceXMLObject != null) {
                _db.removeObject(this.resourceXMLObject.getId());
                _db.store();
            }

        } catch (XMLDBException e) {
            throw new CalDAVException(e.getMessage());
        }
    }
}
