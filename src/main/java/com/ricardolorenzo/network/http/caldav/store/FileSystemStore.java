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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.file.io.IOStreamUtils;
import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.security.acl.FileSystemResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVITransaction;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 * A file backed store for Calendars.
 * 
 * You can control how the store is setup by adding 
 * servlet init-param's to the CalDAVServlet in web.xml
 * 
 * The following init-params are supported
 * 
 * root - directory path to the root of the store.
 * 	Defaults to the users home directory (not a good idea). 
 */

 public class FileSystemStore implements CalDAVStore {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private static int BUF_SIZE = 65536;
    private File root = null;

	public FileSystemStore(ServletConfig config) {
		this.root = new File(System.getProperty("user.home"));
		
		if (config.getInitParameter("root") != null)
		{
			root = new File(config.getInitParameter("root"));
		}
	}

    public CalDAVTransaction begin(final Principal principal) throws CalDAVException {
        if (!this.root.exists()) {
            if (!this.root.mkdirs()) {
                throw new CalDAVException("root path: " + this.root.getAbsolutePath()
                        + " does not exist and could not be created");
            }
        }

        final CalDAVTransaction transaction = new CalDAVITransaction(principal);
        if (!new File(this.root.getAbsolutePath() + File.separator + ".acl.xml").exists()) {
            new FileSystemResourceACL(this, transaction, File.separator);
        }

        return transaction;
    }

    public void checkAuthentication(final CalDAVTransaction transaction) throws SecurityException {
        // do nothing
    }

    public void commit(final CalDAVTransaction transaction) throws CalDAVException {
        // do nothing
    }

    public void createFolder(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        if (!file.mkdir()) {
            throw new CalDAVException("cannot create folder: " + uri);
        }
    }

    public void createResource(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        try {
            if (!file.createNewFile()) {
                throw new CalDAVException("cannot create file: " + uri);
            }
        } catch (final IOException e) {
        	logger.error("uri=" + uri, e);
            throw new CalDAVException(e);
        }
    }

    public String[] getAllChildrenNames(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        String[] childrenNames = new String[] {};
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            final List<String> childList = new ArrayList<String>();
            String name = null;
            for (final File element : children) {
                name = element.getName();
                if (name.startsWith(".")) {
                    continue;
                }
                childList.add(name);
            }
            childrenNames = new String[childList.size()];
            childrenNames = childList.toArray(childrenNames);
            return childrenNames;
        } else {
            return childrenNames;
        }
    }

    public String[] getChildrenNames(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        String[] childrenNames = new String[] {};
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            final List<String> childList = new ArrayList<String>();
            String name = null;
            for (final File element : children) {
                name = element.getName();
                if (name.startsWith(".")) {
                    continue;
                }
                childList.add(name);
            }
            childrenNames = new String[childList.size()];
            childrenNames = childList.toArray(childrenNames);
            return childrenNames;
        } else {
            return childrenNames;
        }
    }

    public CalDAVResourceACL getResourceACL(final CalDAVTransaction transaction, final String uri)
            throws CalDAVException {
        return new FileSystemResourceACL(this, transaction, uri);
    }

    public InputStream getResourceContent(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);

        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
        } catch (final IOException e) {
        	logger.error("uri=" + uri, e);
            throw new CalDAVException(e);
        }
        return in;
    }

    public long getResourceLength(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        return file.length();
    }

    public String getRootPath() {
        return this.root.getAbsolutePath();
    }

    public StoredObject getStoredObject(final CalDAVTransaction transaction, final String uri) {
        StoredObject so = null;

        final StringTokenizer _st = new StringTokenizer(uri, "/");
        while (_st.hasMoreTokens()) {
            final String name = _st.nextToken();
            if ((name != null) && name.startsWith(".")) {
                return so;
            }
        }

        final File file = new File(this.root, uri);
        if (file.exists()) {
            so = new StoredObject();
            so.setFolder(file.isDirectory());
            so.setLastModified(new Date(file.lastModified()));
            so.setCreationDate(new Date(file.lastModified()));
            so.setResourceLength(file.length());
        }

        return so;
    }

    public void removeObject(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        if (file.isDirectory()) {
            final File _acl = new File(file.getAbsolutePath() + "/.acl.xml");
            if (_acl.exists()) {
                if (!_acl.delete()) {
                    throw new CalDAVException("cannot delete object: " + uri);
                }
            }
        }
        if (!file.delete()) {
            throw new CalDAVException("cannot delete object: " + uri);
        }
    }

    public boolean resourceExists(final CalDAVTransaction transaction, final String uri) throws CalDAVException {
        final File file = new File(this.root, uri);
        return file.exists();
    }

    public void rollback(final CalDAVTransaction transaction) throws CalDAVException {
        // do nothing
    }

    public long setResourceContent(final CalDAVTransaction transaction, final String uri, final InputStream is,
            final String contentType, final String characterEncoding) throws CalDAVException {

        final File file = new File(this.root, uri);

        try {
            final OutputStream os = new BufferedOutputStream(new FileOutputStream(file), BUF_SIZE);
            try {
                IOStreamUtils.write(is, os);
            } finally {
                IOStreamUtils.closeQuietly(is);
                IOStreamUtils.closeQuietly(os);
            }
        } catch (final IOException e) {
        	logger.error("uri=" + uri, e);
            throw new CalDAVException(e);
        }

        long length = -1;
        try {
            length = file.length();
        } catch (final SecurityException e) {
        	logger.error("uri=" + uri, e);
            // nothing
        }

        return length;
    }
}
