package com.whitebearsolutions.caldav.method;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;

public class OPTIONS extends CalDAVMethods
{
	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;

	public OPTIONS(CalDAVStore store, ResourceLocksMap resLocks)
	{
		this._store = store;
		this._resource_locks = resLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String tempLockOwner = "OPTIONS" + System.currentTimeMillis() + req.toString();
		String path = getRelativePath(req);
		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			StoredObject so = null;
			try
			{
				resp.addHeader("DAV", "1, 2, access-control, calendar-access");

				so = this._store.getStoredObject(transaction, path);
				String methodsAllowed = determineMethodsAllowed(so);
				resp.addHeader("Allow", methodsAllowed);
				resp.addHeader("MS-Author-Via", "DAV");
			}
			catch (AccessDeniedException _ex)
			{
				resp.sendError(CalDAVResponse.SC_FORBIDDEN);
			}
			catch (CalDAVException _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			}
			finally
			{
				this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
			}
		}
		else
		{
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}