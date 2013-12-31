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

import java.security.Principal;
import java.text.Collator;
import java.util.Collection;
import java.util.Locale;
import java.util.TreeMap;

import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class CalDAVPrivilegeCollection {
    private Principal owner;
    private TreeMap<String, CalDAVPrivilege> privileges;

    protected CalDAVPrivilegeCollection() {
        this.privileges = new TreeMap<String, CalDAVPrivilege>(Collator.getInstance(new Locale("es")));
    }

    public CalDAVPrivilegeCollection(Principal principal) {
        this.owner = principal;
        this.privileges = new TreeMap<String, CalDAVPrivilege>(Collator.getInstance(new Locale("es")));
    }

    public void checkPrincipalPrivilege(Principal principal, String name) throws AccessDeniedException {
        if (!CalDAVPrivilege.getSupportedPrivileges().keySet().contains(name)) {
            throw new AccessDeniedException(name);
        }

        if (this.owner == null) {
            throw new AccessDeniedException(name);
        }

        if ("all".equals(this.owner.getName())) {
            return;
        }

        if (this.owner.getName().equals(principal.getName())) {
            return;
        }

        if (!this.privileges.containsKey(principal.getName())) {
            throw new AccessDeniedException(name);
        }

        CalDAVPrivilege p = this.privileges.get(principal.getName());
        if (p.hasDeniedPrivilege(name)) {
            throw new AccessDeniedException(name);
        }

        if (!p.hasGrantedPrivilege(name)) {
            throw new AccessDeniedException(name);
        }
    }

    public Principal getOwner() {
        return this.owner;
    }

    public Collection<CalDAVPrivilege> getAllPrivileges() {
        return this.privileges.values();
    }

    public CalDAVPrivilege getPrincipalPrivilege(Principal principal) {
        if (this.owner != null && this.owner.getName().equals(principal.getName())) {
            CalDAVPrivilege p = new CalDAVPrivilege(this.owner);
            p.setGrantPrivilege("all");
            return p;
        }
        if (this.privileges.containsKey(principal.getName())) {
            return this.privileges.get(principal.getName());
        }
        return new CalDAVPrivilege(principal);
    }

    protected void setOwner(Principal principal) {
        this.owner = principal;
    }

    public void setPrivilege(CalDAVPrivilege privilege) throws CalDAVException, AccessDeniedException {
        if (privilege.getGrantedPrivileges().isEmpty() && privilege.getDeniedPrivileges().isEmpty()) {
            this.privileges.remove(privilege.getPrincipalName());
        } else {
            this.privileges.put(privilege.getPrincipalName(), privilege);
        }
    }

    public void removePrincipalPrivilege(Principal principal) {
        this.privileges.remove(principal.getName());
    }
}
