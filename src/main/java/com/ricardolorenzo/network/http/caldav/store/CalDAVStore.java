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
package com.ricardolorenzo.network.http.caldav.store;

import java.io.InputStream;
import java.security.Principal;

import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;

/**
 * CalDAV store interface.
 * 
 * @author Ricardo Lorenzo
 */
public interface CalDAVStore {
    /**
     * Indicates that a new request o transaction with this store has been started. Teh requests is
     * ended with the methods {@link #commit()} or {@link #rollback()}.
     * 
     * @param principal
     *            The object <code>java.security.Principal</code> for the request or
     *            <code>null</code> if it is not available.
     * @throws CalDAVException
     */
    CalDAVTransaction begin(Principal principal);

    /**
     * Verify if the authentication data is valid. Otherwise throws an exception
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     */
    void checkAuthentication(CalDAVTransaction transaction);

    /**
     * Return the permission collection for the resource in a <code>CalDAVResurceACL</code> object
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param folder_uri
     *            The folder uri
     */
    CalDAVResourceACL getResourceACL(CalDAVTransaction transaction, String resourceUri);

    /**
     * Gets the root of the object store
     * 
     * @return String with an absolute path of the root store
     */
    String getRootPath();

    /**
     * Indicate that all the changes made will be permanent and any other transaction or resources
     * must finish.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     */
    void commit(CalDAVTransaction transaction);

    /**
     * Indicate that must be remove all the changes and any other transaction or resources must
     * finish.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     */
    void rollback(CalDAVTransaction transaction);

    /**
     * Creates a folder on path <code>folder_uri</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param folder_uri
     *            The folder uri
     */
    void createFolder(CalDAVTransaction transaction, String folder_uri);

    /**
     * Verify if the resource exists on path <code>folder_uri</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param folder_uri
     *            The folder uri
     */
    boolean resourceExists(CalDAVTransaction transaction, String resourceUri);

    /**
     * Creates a resource in the specified path <code>resource_uri</code> .
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param resource_uri
     *            Resource uri
     * @throws CalDAVException
     */
    void createResource(CalDAVTransaction transaction, String resource_uri);

    /**
     * Get the content of the resource on path <code>resource_uri</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param resource_uri
     *            Resource uri
     * @return <code>InputStream</code> where the resource can be read.
     * @throws CalDAVException
     */
    InputStream getResourceContent(CalDAVTransaction transaction, String resourceUri);

    /**
     * Sets / stores the content of the resource specified by <code>resourceUri</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param resource_uri
     *            Resource uri
     * @param content
     *            <code>InputStream</code> to read the resource
     * @param contentType
     *            Mimetype of the resource or <code>null</code> if unknown
     * @param characterEncoding
     *            Character encoding for the resource or <code>null</code> if unknown
     * @return Size of the resource
     * @throws CalDAVException
     */
    long setResourceContent(CalDAVTransaction transaction, String resourceUri, InputStream content, String contentType,
            String characterEncoding);

    /**
     * Gets the names of the childs for the specified folder on path <code>folder_uri</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param folder_uri
     *            The folder uri
     * @return A (posibly empty) list of childs, or <code>null</code> if the path
     *         <code>folder_uri</code> point to a file
     * @throws CalDAVException
     */
    String[] getChildrenNames(CalDAVTransaction transaction, String folder_uri);

    /**
     * Gets the names of the childs for the specified folder on path <code>folder_uri</code>,
     * including the hidden or restricted files.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param folder_uri
     *            The folder uri
     * @return A (posibly empty) list of childs, or <code>null</code> if the path
     *         <code>folder_uri</code> point to a file
     * @throws CalDAVException
     */
    String[] getAllChildrenNames(CalDAVTransaction transaction, String folder_uri);

    /**
     * Gets the maximum size of the resource content on path <code>path</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param path
     *            URI with path
     * @return Size in bytes, <code>-1</code> declares this value as invalid and try to define it
     *         throw the properties if is possible.
     * @throws CalDAVException
     */
    long getResourceLength(CalDAVTransaction transaction, String path);

    /**
     * Deletes the objecton path <code>uri</code>.
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param uri
     *            Object uri
     * @throws WebdavException
     *             if something goes wrong on the store level
     */
    void removeObject(CalDAVTransaction transaction, String uri);

    /**
     * Gets the storedObject specified by <code>uri</code>
     * 
     * @param transaction
     *            Indicates that the method is executed on a CalDAV transaction
     * @param uri
     *            URI
     * @return StoredObject
     */
    StoredObject getStoredObject(CalDAVTransaction transaction, String uri);
}
