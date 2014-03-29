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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockedObject {
	
	   Logger logger = LoggerFactory.getLogger(LockedObject.class);

    private ResourceLocksMap _resource_locks;
    private String _path;
    private String _id;

    /**
     * The depth of the locked collection. If the locked resource is not a collection or folder, the
     * depth will be 0.
     */
    protected int _lock_depth;

    /**
     * Maximum object lock time in milliseconds
     */
    protected long _expiresAt;

    /**
     * Lock owners. The shared locks can have multiple owners. If no owners, the value will be
     * <code>null</code>.
     */
    protected List<String> _owner = null;

    /**
     * Child locks
     */
    protected List<LockedObject> _children = null;
    protected LockedObject _parent = null;

    /**
     * Specifies if is it a exclusive lock or not. If the owner is <code>null</code> this value does
     * not matter.
     */
    protected boolean _exclusive = false;

    /**
     * Specifies if the lock is for read or write
     */
    protected String _type = null;

    /**
     * @param resLocks
     *            The <code>ResourceLocksMap</code> where the locks are stored
     * @param path
     *            The path of the locked object
     * @param temporary
     *            Indicate if the <code>LockedObject</code> is temporary or not
     */
    public LockedObject(ResourceLocksMap resLocks, String path, boolean temporary) {
        this._path = path;
        this._id = UUID.randomUUID().toString();
        this._resource_locks = resLocks;
        this._owner = new ArrayList<String>();
        this._children = new ArrayList<LockedObject>();

        if (!temporary) {
            this._resource_locks._locks.put(path, this);
            this._resource_locks._id_locks.put(_id, this);
        } else {
            this._resource_locks._temp_locks.put(path, this);
            this._resource_locks._id_temp_locks.put(_id, this);
        }
        this._resource_locks._cleanup_count++;
    }

    /**
     * Add a new owner to the lock
     * 
     * @param owner
     *            cadena que representa al propietario
     * @return verdadero si el propietario ha sido a&ntilde;adido, en otro caso falso
     */
    public boolean addLockedObjectOwner(String owner) {
        this._owner.add(owner);
        return true;
    }

    /**
     * Try to delete the lock owner
     * 
     * @param owner
     *            cadena que representa al propietario
     */
    public void removeLockedObjectOwner(String owner) {
        try {
            if (this._owner.isEmpty()) {
                this._owner.remove(owner);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
        	logger.error("removeLockedObjectOwner", e);
        }
    }

    /**
     * Add a child lock to this lock
     * 
     * @param newChild
     *            nuevo hijo
     */
    public void addChild(LockedObject newChild) {
        this._children.add(newChild);
    }

    /**
     * Delete this lock object, asuming that do not have cholds or owners
     * 
     */
    public void removeLockedObject() {
        if (this != this._resource_locks._root && !this.getPath().equals("/")) {
            this._children.remove(this);

            // removing from hashtable
            this._resource_locks._id_locks.remove(getID());
            this._resource_locks._locks.remove(getPath());
            // now the garbage collector has some work to do
        }
    }

    /**
     * Delete this lock object, asuming that do not have cholds or owners
     * 
     */
    public void removeTempLockedObject() {
        if (this != this._resource_locks._temp_root) {
            // removing from tree
            if (this._parent != null && this._parent._children != null) {
                this._children.remove(true);

                // removing from hashtable
                this._resource_locks._id_temp_locks.remove(getID());
                this._resource_locks._temp_locks.remove(getPath());
                // now the garbage collector has some work to do
            }
        }
    }

    /**
     * Verify if a exclusive lock can be assign, just considering child up to "depth"
     * 
     * @param exclusive
     *            If the new lock must be exclusive
     * @param depth
     *            The check depth
     * @return True if the lock can be assign
     */
    public boolean checkLocks(boolean exclusive, int depth) {
        if (checkParents(exclusive) && checkChildren(exclusive, depth)) {
            return true;
        }
        return false;
    }

    /**
     * Auxiliary method for checkLocks(). Verify if the parents are locked or not.
     * 
     * @param exclusive
     *            If the new lock must be exclusive
     * @return True if none of parent block is forbidden or new
     */
    private boolean checkParents(boolean exclusive) {
        if (this._path.equals("/")) {
            return true;
        } else {
            if (this._owner == null) {
                // no owner, checking parents
                return this._parent != null && this._parent.checkParents(exclusive);
            } else {
                // there already is a owner
                return !(this._exclusive || exclusive) && this._parent.checkParents(exclusive);
            }
        }
    }

    /**
     * Auxiliary method for checkLocks(). Verify if childs are locked
     * 
     * @param exclusive
     *            si el nuevo bloqueo debe ser exclusivo
     * @param depth
     *            la profundidad que se debe verificar
     * @return verdadero si no existen bloqueos en los hijos
     */
    private boolean checkChildren(boolean exclusive, int depth) {
        if (this._children.isEmpty()) {
            // a file
            return this._owner.isEmpty() || !(this._exclusive || exclusive);
        } else {
            // a folder
            if (this._owner == null) {
                // no owner, checking children
                if (depth != 0) {
                    boolean canLock = true;
                    for (LockedObject locked : this._children) {
                        if (!locked.checkChildren(exclusive, depth - 1)) {
                            canLock = false;
                        }
                    }
                    return canLock;
                } else {
                    // depth == 0 -> we don't care for children
                    return true;
                }
            } else {
                // there already is a owner
                return !(this._exclusive || exclusive);
            }
        }
    }

    /**
     * Defines a new timout for <code>LockedObject</code>
     * 
     * @param timeout
     */
    public void refreshTimeout(int timeout) {
        this._expiresAt = System.currentTimeMillis() + (timeout * 1000);
    }

    /**
     * Get the timeout for <code>LockedObject</code>
     * 
     * @return timeout
     */
    public long getTimeoutMillis() {
        return (this._expiresAt - System.currentTimeMillis());
    }

    /**
     * Returns true or false if the lock is expired
     * 
     * @return True if the lock is expired
     */
    public boolean hasExpired() {
        if (this._expiresAt != 0) {
            return (System.currentTimeMillis() > _expiresAt);
        } else {
            return true;
        }
    }

    /**
     * Get the lock ID (locktoken) for <code>LockedObject</code>
     * 
     * @return locktoken
     */
    public String getID() {
        return this._id;
    }

    /**
     * Get the owners for <code>LockedObject</code>
     * 
     * @return owners
     */
    public String[] getOwner() {
        return (String[]) this._owner.toArray(new String[0]);
    }

    /**
     * Get the path for <code>LockedObject</code>
     * 
     * @return path
     */
    public String getPath() {
        return this._path;
    }

    /**
     * Defines if <code>LockedObject</code> is exclusive
     * 
     * @param exclusive
     */
    public void setExclusive(boolean exclusive) {
        this._exclusive = exclusive;
    }

    /**
     * Get true or false if <code>LockedObject</code> is exclusive
     * 
     * @return exclusivity
     */
    public boolean isExclusive() {
        return this._exclusive;
    }

    /**
     * Get true or false if <code>LockedObject</code> is shared
     * 
     * @return exclusivity
     */
    public boolean isShared() {
        return !this._exclusive;
    }

    /**
     * Get the lock type
     * 
     * @return type
     */
    public String getType() {
        return this._type;
    }

    /**
     * Get the lock depth
     * 
     * @return depth
     */
    public int getLockDepth() {
        return this._lock_depth;
    }
}
