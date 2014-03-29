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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.network.http.caldav.store.StoredObject;

public abstract class CalDAVMethods extends CalDAVAbstractMethod {
	private final static Logger logger = LoggerFactory.getLogger(CalDAVMethods.class);
    private static final String NULL_RESOURCE_METHODS_ALLOWED = "OPTIONS, MKCOL, PUT, PROPFIND, LOCK, UNLOCK";
    private static final String RESOURCE_METHODS_ALLOWED = "OPTIONS, GET, HEAD, POST, DELETE, TRACE"
            + ", PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, ACL, REPORT";
    private static final String FOLDER_METHOD_ALLOWED = ", PUT";
    private static final String LESS_ALLOWED_METHODS = "OPTIONS, MKCOL, PUT";

    /**
     * Determine the allowest methods for the resource.
     * 
     * @param so
     *            StoredObject resource object
     * @return All methods comma separated
     */
    protected static String determineMethodsAllowed(StoredObject so) {
        try {
            if (so != null) {
                if (so.isNullResource()) {
                    return NULL_RESOURCE_METHODS_ALLOWED;
                } else if (so.isFolder()) {
                    return RESOURCE_METHODS_ALLOWED + FOLDER_METHOD_ALLOWED;
                }
                return RESOURCE_METHODS_ALLOWED;
            }
        } catch (Exception e) {
        	logger.error("determineMethosAllows", e);
            // nothing
        }
        return LESS_ALLOWED_METHODS;
    }
}
