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

import com.ricardolorenzo.network.http.caldav.CalDAVException;
import com.ricardolorenzo.network.http.caldav.security.acl.CalDAVResourceACL;
import com.ricardolorenzo.network.http.caldav.security.acl.FileSystemResourceACL;
import com.ricardolorenzo.network.http.caldav.session.CalDAVITransaction;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class FileSystemStore implements CalDAVStore {
    private static int BUF_SIZE = 65536;
    private File root = null;

    public FileSystemStore(File root) {
        this.root = root;
    }

    public CalDAVTransaction begin(Principal principal) throws CalDAVException {
        if (!this.root.exists()) {
            if (!this.root.mkdirs()) {
                throw new CalDAVException("root path: " + this.root.getAbsolutePath()
                        + " does not exist and could not be created");
            }
        }

        CalDAVTransaction transaction = new CalDAVITransaction(principal);
        if (!new File(this.root.getAbsolutePath() + File.separator + ".acl.xml").exists()) {
            new FileSystemResourceACL(this, transaction, File.separator);
        }

        return transaction;
    }

    public CalDAVResourceACL getResourceACL(CalDAVTransaction transaction, String uri) throws CalDAVException {
        return new FileSystemResourceACL(this, transaction, uri);
    }

    public String getRootPath() {
        return this.root.getAbsolutePath();
    }

    public void checkAuthentication(CalDAVTransaction transaction) throws SecurityException {
        // do nothing
    }

    public void commit(CalDAVTransaction transaction) throws CalDAVException {
        // do nothing
    }

    public void rollback(CalDAVTransaction transaction) throws CalDAVException {
        // do nothing
    }

    public void createFolder(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        if (!file.mkdir()) {
            throw new CalDAVException("cannot create folder: " + uri);
        }
    }

    public void createResource(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        try {
            if (!file.createNewFile()) {
                throw new CalDAVException("cannot create file: " + uri);
            }
        } catch (IOException e) {
            throw new CalDAVException(e);
        }
    }

    public long setResourceContent(CalDAVTransaction transaction, String uri, InputStream is, String contentType,
            String characterEncoding) throws CalDAVException {

        File file = new File(this.root, uri);
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(file), BUF_SIZE);
            try {
                int read;
                byte[] copyBuffer = new byte[BUF_SIZE];

                while ((read = is.read(copyBuffer, 0, copyBuffer.length)) != -1) {
                    os.write(copyBuffer, 0, read);
                }
            } finally {
                try {
                    is.close();
                } finally {
                    os.close();
                }
            }
        } catch (IOException e) {
            throw new CalDAVException(e);
        }
        long length = -1;

        try {
            length = file.length();
        } catch (SecurityException e) {
            // nothing
        }

        return length;
    }

    public String[] getChildrenNames(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        String[] childrenNames = new String[] {};
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            List<String> childList = new ArrayList<String>();
            String name = null;
            for (int i = 0; i < children.length; i++) {
                name = children[i].getName();
                if (name.startsWith(".")) {
                    continue;
                }
                childList.add(name);
            }
            childrenNames = new String[childList.size()];
            childrenNames = (String[]) childList.toArray(childrenNames);
            return childrenNames;
        } else {
            return childrenNames;
        }
    }

    public String[] getAllChildrenNames(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        String[] childrenNames = new String[] {};
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            List<String> childList = new ArrayList<String>();
            String name = null;
            for (int i = 0; i < children.length; i++) {
                name = children[i].getName();
                if (name.startsWith(".")) {
                    continue;
                }
                childList.add(name);
            }
            childrenNames = new String[childList.size()];
            childrenNames = (String[]) childList.toArray(childrenNames);
            return childrenNames;
        } else {
            return childrenNames;
        }
    }

    public void removeObject(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        if (file.isDirectory()) {
            File _acl = new File(file.getAbsolutePath() + "/.acl.xml");
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

    public boolean resourceExists(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        return file.exists();
    }

    public InputStream getResourceContent(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);

        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
        } catch (IOExceptione) {
            throw new CalDAVException(_ex);
        }
        return in;
    }

    public long getResourceLength(CalDAVTransaction transaction, String uri) throws CalDAVException {
        File file = new File(this.root, uri);
        return file.length();
    }

    public StoredObject getStoredObject(CalDAVTransaction transaction, String uri) {
        StoredObject so = null;

        StringTokenizer _st = new StringTokenizer(uri, "/");
        while (_st.hasMoreTokens()) {
            String name = _st.nextToken();
            if (name != null && name.startsWith(".")) {
                return so;
            }
        }

        File file = new File(this.root, uri);
        if (file.exists()) {
            so = new StoredObject();
            so.setFolder(file.isDirectory());
            so.setLastModified(new Date(file.lastModified()));
            so.setCreationDate(new Date(file.lastModified()));
            so.setResourceLength(file.length());
        }

        return so;
    }
}
