package com.whitebearsolutions.caldav.method;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.LockedObject;
import com.whitebearsolutions.caldav.locking.ResourceLocks;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;

public class UNLOCK extends CalDAVMethods
{
	private CalDAVStore _store;
	private ResourceLocks _resource_locks;

	public UNLOCK(CalDAVStore store, ResourceLocks resourceLocks)
	{
		this._store = store;
		this._resource_locks = resourceLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getRelativePath(req);
		String tempLockOwner = "UNLOCK" + System.currentTimeMillis() + req.toString();
		try
		{
			if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
			{
				String lockId = getLockIdFromLockTokenHeader(req);
				LockedObject lo;
				if (lockId != null && ((lo = this._resource_locks.getLockedObjectByID(transaction, lockId)) != null))
				{
					String[] owners = lo.getOwner();
					String owner = null;
					if (lo.isShared())
					{
						// more than one owner is possible
						if (owners != null)
						{
							for (int i = 0; i < owners.length; i++)
							{
								// remove owner from LockedObject
								lo.removeLockedObjectOwner(owners[i]);
							}
						}
					}
					else
					{
						// exclusive, only one lock owner
						if (owners != null)
							owner = owners[0];
						else
							owner = null;
					}

					if (this._resource_locks.unlock(transaction, lockId, owner))
					{
						StoredObject so = this._store.getStoredObject(transaction, path);
						if (so.isNullResource())
						{
							this._store.removeObject(transaction, path);
						}

						resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
					}
					else
					{
						resp.sendError(CalDAVResponse.SC_METHOD_FAILURE);
					}
				}
				else
				{
					resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
				}
			}
		}
		catch (LockException _ex)
		{
			logger.error(_ex);
		}
		finally
		{
			this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
		}
	}
}