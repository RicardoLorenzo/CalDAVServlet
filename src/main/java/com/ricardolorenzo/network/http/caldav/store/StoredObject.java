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

import java.util.Date;

/**
 * 
 * Represents an object in the store.
 * 
 * @author Ricardo Lorenzo
 */

public class StoredObject {
    private boolean is_folder;
    private Date last_modified;
    private Date creation_date;
    private long content_length;

    private boolean is_null_ressource;

    /**
     * Determines whether the object is a folder or resource.
     * 
     * @return True if the object is a folder
     */
    public boolean isFolder() {
        return this.is_folder;
    }

    /**
     * Determines whether the object is a folder or resource.
     * 
     * @return True if the object is a resource
     */
    public boolean isResource() {
        return !this.is_folder;
    }

    /**
     * Defines if the object is a folder or resource.
     * 
     * @param value
     *            True - folder ; false - resource
     */
    public void setFolder(boolean value) {
        this.is_folder = value;
    }

    /**
     * Gets the last modified date
     * 
     * @return <code>Date</code> last modified date
     */
    public Date getLastModified() {
        return this.last_modified;
    }

    /**
     * Defines the last modified date
     * 
     * @param d
     *            Last modified date
     */
    public void setLastModified(Date d) {
        this.last_modified = d;
    }

    /**
     * Gets the creation date
     * 
     * @return <code>Date</code> creation date
     */
    public Date getCreationDate() {
        return this.creation_date;
    }

    /**
     * Defines the creation date
     * 
     * @param date
     *            Creation date
     */
    public void setCreationDate(Date date) {
        this.creation_date = date;
    }

    /**
     * Gets the size of the resource
     * 
     * @return Creation date
     */
    public long getResourceLength() {
        return this.content_length;
    }

    /**
     * Defines the size of the resource content
     * 
     * @param l
     *            Size of the resource content
     */
    public void setResourceLength(long l) {
        this.content_length = l;
    }

    /**
     * Gets the resource status
     * 
     * @return True if the resource is locked or <code>null</code>
     */
    public boolean isNullResource() {
        return this.is_null_ressource;
    }

    /**
     * Defines the object as a lock or <code>null</code>
     * 
     * @param f
     *            True to define the resource as a locked or <code>null</code>
     */
    public void setNullResource(boolean f) {
        this.is_null_ressource = f;
        this.is_folder = false;
        this.creation_date = null;
        this.last_modified = null;
        // this.content = null;
        this.content_length = 0;
    }
}
