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

import java.util.List;
import java.util.Map;

import com.ricardolorenzo.network.http.caldav.AccessDeniedException;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public interface CalDAVResourceACL {
    /**
     * Get an <code>CalDAVPrivilegeCollection</code> object with all the ACL information for the
     * resource
     * 
     * @param transaction
     * @throws ACLException
     */
    CalDAVPrivilegeCollection getPrivilegeCollection();

    /**
     * Get the privilege collection available.
     * 
     * @param transaction
     * @throws ACLException
     */
    Map<String, String> getSupportedPrivilegeSet();

    /**
     * Get the URL collection for the available user or group identifiers on the server
     * 
     * @param transaction
     * @throws ACLException
     */
    List<String> getPrincipalCollectionSet();

    /**
     * Defines the <code>CalDAVPrivilegeCollection</code> object with all the ACL information for
     * the resource
     * 
     * @param transaction
     * @throws ACLException
     */
    void setPrivilegeCollection(CalDAVTransaction transaction, CalDAVPrivilegeCollection collection)
            throws ACLException, AccessDeniedException;

    void removeCollection(CalDAVTransaction transaction) throws CalDAVException;
}
