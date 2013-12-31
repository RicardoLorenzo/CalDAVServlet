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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class CalDAVPrivilege {
    private Principal principal;
    private List<String> grant;
    private List<String> deny;
    private final static Map<String, String> supportedPrivileges;
    static {
        supportedPrivileges = new HashMap<String, String>();
        supportedPrivileges.put("all", "Any operation");
        supportedPrivileges.put("read", "Read any object");
        supportedPrivileges.put("read-acl", "Read ACL");
        supportedPrivileges.put("read-current-user-privilege-set", "Read current privilege set property");
        supportedPrivileges.put("write", "Write any object");
        supportedPrivileges.put("write-acl", "Write ACL");
        supportedPrivileges.put("write-properties", "Write properties");
        supportedPrivileges.put("write-content", "Write resource content");
        supportedPrivileges.put("unlock", "Unlock resource");
    }

    public CalDAVPrivilege() throws ACLException {
        this.grant = new ArrayList<String>();
        this.deny = new ArrayList<String>();
    }

    public CalDAVPrivilege(Principal principal) {
        this.principal = principal;
        this.grant = new ArrayList<String>();
        this.deny = new ArrayList<String>();
    }

    public static Map<String, String> getSupportedPrivileges() {
        return supportedPrivileges;
    }

    public Principal getPrincipal() {
        return this.principal;
    }

    public String getPrincipalName() {
        return this.principal.getName();
    }

    public List<String> getDeniedPrivileges() {
        return this.deny;
    }

    public List<String> getGrantedPrivileges() {
        return this.grant;
    }

    public boolean hasDeniedPrivilege(String privilege) {
        if (this.deny.contains(privilege)) {
            return true;
        }
        return false;
    }

    public boolean hasGrantedPrivilege(String privilege) {
        if (this.grant.contains(privilege)) {
            return true;
        }
        return false;
    }

    public void removeAllDeniedPrivileges() {
        this.deny = new ArrayList<String>();
    }

    public void removeAllGrantedPrivileges() {
        this.grant = new ArrayList<String>();
    }

    public void removeDeniedPrivilege(String privilege) {
        this.deny.remove(privilege);
    }

    public void removeGrantedPrivilege(String privilege) {
        this.grant.remove(privilege);
    }

    public void setDenyPrivileges(List<String> privileges) throws ACLException {
        for (String p : privileges) {
            if (!supportedPrivileges.containsKey(p)) {
                throw new ACLException("privilege \"" + p + "\" not supported");
            }
        }

        this.deny = privileges;
    }

    public void setDenyPrivilege(String privilege) throws ACLException {
        if (!supportedPrivileges.containsKey(privilege)) {
            throw new ACLException("privilege \"" + privilege + "\" not supported");
        }

        if (hasGrantedPrivilege(privilege)) {
            removeGrantedPrivilege(privilege);
        }

        if (!hasDeniedPrivilege(privilege)) {
            this.deny.add(privilege);
        }
    }

    public void setGrantPrivileges(List<String> privileges) throws ACLException {
        for (String p : privileges) {
            if (!supportedPrivileges.containsKey(p)) {
                throw new ACLException("privilege \"" + p + "\" not supported");
            }
        }

        this.grant = privileges;
    }

    public void setGrantPrivilege(String privilege) throws ACLException {
        if (!supportedPrivileges.containsKey(privilege)) {
            throw new ACLException("privilege \"" + privilege + "\" not supported");
        }

        if (!hasGrantedPrivilege(privilege)) {
            this.grant.add(privilege);
        }
    }

    public void setPrincipal(Principal principal) {
        this.principal = principal;
    }
}
