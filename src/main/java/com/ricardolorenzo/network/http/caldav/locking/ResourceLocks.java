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
package com.ricardolorenzo.network.http.caldav.locking;

import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public interface ResourceLocks {
    /**
     * Try to lock the resource on "path".
     * 
     * @param transaction
     * @param path
     *            Path for the resource
     * @param owner
     *            Owner of the lock
     * @param exclusive
     *            Defines if the lock is exclusive
     * @param depth
     *            Lock depth
     * @param timeout
     *            Lock timeout in seconds.
     * @return true if the resource at path was successfully locked, false if an existing lock
     *         prevented this
     * @throws LockException
     */
    boolean lock(CalDAVTransaction transaction, String path, String owner, boolean exclusive, int depth, int timeout,
            boolean temporary) throws LockException;

    /**
     * Unlock a resource by "id" (and all subfolders if exists) with the same owner
     * 
     * @param transaction
     * @param id
     *            Resource identifier
     * @param owner
     *            The owner of the resource
     */
    boolean unlock(CalDAVTransaction transaction, String id, String owner);

    /**
     * Unlock a resource by "path" (and all subfolders if exists) with the same owner
     * 
     * @param transaction
     * @param path
     *            Path of the resource
     * @param owner
     *            The owner of the resource
     */
    void unlockTemporaryLockedObjects(CalDAVTransaction transaction, String path, String owner);

    /**
     * Deletes all the <code>LockedObject</code> with expired timeout
     * 
     * @param transaction
     * @param temporary
     *            verifica los timeout en bloqueos temporales o reales
     */
    void checkTimeouts(CalDAVTransaction transaction, boolean temporary);

    /**
     * Try to lock exclusive a resource on "path"
     * 
     * @param transaction
     *            Transaction
     * @param path
     *            Path of the resource
     * @param owner
     *            Owner of the lock
     * @param depth
     *            Depth
     * @param timeout
     *            Timeout in seconds.
     * @return True if the resource on the path was successfully locked, false if other lock exists
     * @throws LockException
     */
    boolean exclusiveLock(CalDAVTransaction transaction, String path, String owner, int depth, int timeout)
            throws LockException;

    /**
     * Intenta bloquear el recurso en "path" de forma compartida (shared)
     * 
     * @param transaction
     *            Transaction
     * @param path
     *            ruta del recurso a bloquear
     * @param owner
     *            propietario del bloqueo
     * @param depth
     *            depth
     * @param timeout
     *            duraci&oacute;n en segundos.
     * @return verdadero si el recurso en la ruta ha sido bloqueado, falso si si existe un bloqueo
     *         que lo previene
     * @throws LockException
     */
    boolean sharedLock(CalDAVTransaction transaction, String path, String owner, int depth, int timeout)
            throws LockException;

    /**
     * Get the <code>LockedObject</code> for the resource identifier
     * 
     * @param transaction
     * @param id
     *            Resource identifier
     * @return LockedObject or <code>null</code> if does not exists another
     *         <code>LockedObject</code> with the same identifier
     */
    LockedObject getLockedObjectByID(CalDAVTransaction transaction, String id);

    /**
     * Get the <code>LockedObject</code> for the path
     * 
     * @param transaction
     * @param path
     *            Resource path
     * @return LockedObject or <code>null</code> if does not exists another
     *         <code>LockedObject</code> in this path
     */
    LockedObject getLockedObjectByPath(CalDAVTransaction transaction, String path);

    /**
     * Get the temporary <code>LockedObject</code> for the resource identifier
     * 
     * @param transaction
     * @param id
     *            Resource identifier
     * @return LockedObject or <code>null</code> if does not exists another
     *         <code>LockedObject</code> with the same identifier
     */
    LockedObject getTempLockedObjectByID(CalDAVTransaction transaction, String id);

    /**
     * Get the temporary <code>LockedObject</code> for the path
     * 
     * @param transaction
     * @param path
     *            rResource path
     * @return LockedObject or <code>null</code> if does not exists another
     *         <code>LockedObject</code> in this path
     */
    LockedObject getTempLockedObjectByPath(CalDAVTransaction transaction, String path);
}
