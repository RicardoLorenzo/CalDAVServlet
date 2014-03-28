package com.whitebearsolutions.caldav.locking;

import java.util.Enumeration;
import java.util.Hashtable;

import com.whitebearsolutions.caldav.session.CalDAVTransaction;

public class ResourceLocksMap implements ResourceLocks
{
	/**
	 * after creating this much LockedObjects, a cleanup deletes unused
	 * LockedObjects
	 */
	private final int _cleanup_limit = 100000;
	protected int _cleanup_count = 0;

	/**
	 * keys: path value: LockedObject from that path
	 */
	protected Hashtable<String, LockedObject> _locks = new Hashtable<String, LockedObject>();

	/**
	 * keys: id value: LockedObject from that id
	 */
	protected Hashtable<String, LockedObject> _id_locks = new Hashtable<String, LockedObject>();

	/**
	 * keys: path value: Temporary LockedObject from that path
	 */
	protected Hashtable<String, LockedObject> _temp_locks = new Hashtable<String, LockedObject>();

	/**
	 * keys: id value: Temporary LockedObject from that id
	 */
	protected Hashtable<String, LockedObject> _id_temp_locks = new Hashtable<String, LockedObject>();

	// REMEMBER TO REMOVE UNUSED LOCKS FROM THE HASHTABLE AS WELL

	protected LockedObject _root = null;
	protected LockedObject _temp_root = null;
	private boolean _temporary = true;

	public ResourceLocksMap()
	{
		this._root = new LockedObject(this, "/", true);
		this._temp_root = new LockedObject(this, "/", false);
	}

	public synchronized boolean lock(CalDAVTransaction transaction, String path, String owner, boolean exclusive,
			int depth, int timeout, boolean temporary) throws LockException
	{

		LockedObject lo = null;
		if (temporary)
		{
			lo = generateTempLockedObjects(transaction, path);
			lo._type = "read";
		}
		else
		{
			lo = generateLockedObjects(transaction, path);
			lo._type = "write";
		}

		if (lo.checkLocks(exclusive, depth))
		{
			lo._exclusive = exclusive;
			lo._lock_depth = depth;
			lo._expiresAt = System.currentTimeMillis() + (timeout * 1000);
			if (lo._parent != null)
			{
				lo._parent._expiresAt = lo._expiresAt;
				if (lo._parent.equals(this._root))
				{
					LockedObject rootLo = getLockedObjectByPath(transaction, _root.getPath());
					rootLo._expiresAt = lo._expiresAt;
				}
				else if (lo._parent.equals(this._temp_root))
				{
					LockedObject tempRootLo = getTempLockedObjectByPath(transaction, this._temp_root.getPath());
					tempRootLo._expiresAt = lo._expiresAt;
				}
			}
			if (lo.addLockedObjectOwner(owner))
			{
				return true;
			}
			else
			{
				return false;
			}
		}
		else
		{
			// can not lock
			return false;
		}
	}

	public synchronized boolean unlock(CalDAVTransaction transaction, String id, String owner)
	{
		if (this._id_locks.containsKey(id))
		{
			String path = this._id_locks.get(id).getPath();
			if (this._locks.containsKey(path))
			{
				LockedObject lo = this._locks.get(path);
				lo.removeLockedObjectOwner(owner);

				if (lo._children == null && lo._owner == null)
					lo.removeLockedObject();

			}
			else
			{
				// there is no lock at that path. someone tried to unlock it
				// anyway. could point to a problem
				return false;
			}

			if (this._cleanup_count > this._cleanup_limit)
			{
				this._cleanup_count = 0;
				cleanLockedObjects(transaction, this._root, !this._temporary);
			}
		}
		checkTimeouts(transaction, !_temporary);

		return true;

	}

	public synchronized void unlockTemporaryLockedObjects(CalDAVTransaction transaction, String path, String owner)
	{
		if (this._temp_locks.containsKey(path))
		{
			LockedObject lo = this._temp_locks.get(path);
			lo.removeLockedObjectOwner(owner);

		}

		if (this._cleanup_count > this._cleanup_limit)
		{
			this._cleanup_count = 0;
			cleanLockedObjects(transaction, this._temp_root, this._temporary);
		}

		checkTimeouts(transaction, this._temporary);
	}

	public void checkTimeouts(CalDAVTransaction transaction, boolean temporary)
	{
		if (!temporary)
		{
			Enumeration<LockedObject> lockedObjects = this._locks.elements();
			while (lockedObjects.hasMoreElements())
			{
				LockedObject currentLockedObject = lockedObjects.nextElement();

				if (currentLockedObject._expiresAt < System.currentTimeMillis())
				{
					currentLockedObject.removeLockedObject();
				}
			}
		}
		else
		{
			Enumeration<LockedObject> lockedObjects = this._temp_locks.elements();
			while (lockedObjects.hasMoreElements())
			{
				LockedObject currentLockedObject = lockedObjects.nextElement();

				if (currentLockedObject._expiresAt < System.currentTimeMillis())
				{
					currentLockedObject.removeTempLockedObject();
				}
			}
		}

	}

	public boolean exclusiveLock(CalDAVTransaction transaction, String path, String owner, int depth, int timeout)
			throws LockException
	{
		return lock(transaction, path, owner, true, depth, timeout, false);
	}

	public boolean sharedLock(CalDAVTransaction transaction, String path, String owner, int depth, int timeout)
			throws LockException
	{
		return lock(transaction, path, owner, false, depth, timeout, false);
	}

	public LockedObject getLockedObjectByID(CalDAVTransaction transaction, String id)
	{
		if (this._id_locks.containsKey(id))
		{
			return this._id_locks.get(id);
		}
		else
		{
			return null;
		}
	}

	public LockedObject getLockedObjectByPath(CalDAVTransaction transaction, String path)
	{
		if (this._locks.containsKey(path))
		{
			return (LockedObject) this._locks.get(path);
		}
		else
		{
			return null;
		}
	}

	public LockedObject getTempLockedObjectByID(CalDAVTransaction transaction, String id)
	{
		if (this._id_temp_locks.containsKey(id))
		{
			return this._id_temp_locks.get(id);
		}
		else
		{
			return null;
		}
	}

	public LockedObject getTempLockedObjectByPath(CalDAVTransaction transaction, String path)
	{
		if (this._temp_locks.containsKey(path))
		{
			return (LockedObject) this._temp_locks.get(path);
		}
		else
		{
			return null;
		}
	}

	/**
	 * generates real LockedObjects for the resource at path and its parent
	 * folders. does not create new LockedObjects if they already exist
	 * 
	 * @param transaction
	 * @param path
	 *            path to the (new) LockedObject
	 * @return the LockedObject for path.
	 */
	private LockedObject generateLockedObjects(CalDAVTransaction transaction, String path)
	{
		if (!this._locks.containsKey(path))
		{
			LockedObject returnObject = new LockedObject(this, path, !this._temporary);
			String parentPath = getParentPath(path);
			if (parentPath != null)
			{
				LockedObject parentLockedObject = generateLockedObjects(transaction, parentPath);
				parentLockedObject.addChild(returnObject);
				returnObject._parent = parentLockedObject;
			}
			return returnObject;
		}
		else
		{
			// there is already a LockedObject on the specified path
			return (LockedObject) this._locks.get(path);
		}

	}

	/**
	 * generates temporary LockedObjects for the resource at path and its parent
	 * folders. does not create new LockedObjects if they already exist
	 * 
	 * @param transaction
	 * @param path
	 *            path to the (new) LockedObject
	 * @return the LockedObject for path.
	 */
	private LockedObject generateTempLockedObjects(CalDAVTransaction transaction, String path)
	{
		if (!this._temp_locks.containsKey(path))
		{
			LockedObject returnObject = new LockedObject(this, path, _temporary);
			String parentPath = getParentPath(path);
			if (parentPath != null)
			{
				LockedObject parentLockedObject = generateTempLockedObjects(transaction, parentPath);
				parentLockedObject.addChild(returnObject);
				returnObject._parent = parentLockedObject;
			}
			return returnObject;
		}
		else
		{
			// there is already a LockedObject on the specified path
			return (LockedObject) this._temp_locks.get(path);
		}

	}

	/**
	 * deletes unused LockedObjects and resets the counter. works recursively
	 * starting at the given LockedObject
	 * 
	 * @param transaction
	 * @param lo
	 *            LockedObject
	 * @param temporary
	 *            Clean temporary or real locks
	 * 
	 * @return if cleaned
	 */
	private boolean cleanLockedObjects(CalDAVTransaction transaction, LockedObject lo, boolean temporary)
	{
		if (!lo._children.isEmpty())
		{
			if (!lo._owner.isEmpty())
			{
				if (temporary)
				{
					lo.removeTempLockedObject();
				}
				else
				{
					lo.removeLockedObject();
				}

				return true;
			}
			else
			{
				return false;
			}
		}
		else
		{
			boolean canDelete = true;
			for (LockedObject locked : lo._children)
			{
				if (!cleanLockedObjects(transaction, locked, temporary))
				{
					canDelete = false;
				}
			}
			if (canDelete)
			{
				if (lo._owner == null)
				{
					if (temporary)
					{
						lo.removeTempLockedObject();
					}
					else
					{
						lo.removeLockedObject();
					}
					return true;
				}
				else
				{
					return false;
				}
			}
			else
			{
				return false;
			}
		}
	}

	/**
	 * creates the parent path from the given path by removing the last '/' and
	 * everything after that
	 * 
	 * @param path
	 *            the path
	 * @return parent path
	 */
	private String getParentPath(String path)
	{
		int slash = path.lastIndexOf('/');
		if (slash == -1)
		{
			return null;
		}
		else
		{
			if (slash == 0)
			{
				// return "root" if parent path is empty string
				return "/";
			}
			else
			{
				return path.substring(0, slash);
			}
		}
	}
}