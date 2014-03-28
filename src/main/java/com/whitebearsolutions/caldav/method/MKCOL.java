package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.CalDAVServlet;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.LockedObject;
import com.whitebearsolutions.caldav.locking.ResourceLocks;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;

public class MKCOL extends CalDAVAbstractMethod
{
	private CalDAVStore _store;
	private ResourceLocks _resource_locks;
	private CalDAVResourceACL resource_acl;

	public MKCOL(CalDAVStore store, ResourceLocks resourceLocks)
	{
		this._store = store;
		this._resource_locks = resourceLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getRelativePath(req);
		String parentPath = getParentPath(getCleanPath(path));
		this.resource_acl = this._store.getResourceACL(transaction, path);

		if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath))
		{
			resp.sendError(CalDAVResponse.SC_FORBIDDEN);
			return;
		}

		String tempLockOwner = "MKCOL" + System.currentTimeMillis() + req.toString();
		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(CalDAVServlet.provider.getUserPrincipal(req), "read");

				mkcol(transaction, req, resp);
			}
			catch (AccessDeniedException _ex)
			{
				sendPrivilegeError(resp, path, _ex.getMessage());
			}
			catch (Exception _ex)
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

	public void mkcol(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp) throws Exception
	{
		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
		String path = getRelativePath(req);
		String parentPath = getParentPath(getCleanPath(path));
		StoredObject parentSo, so = null;

		parentSo = this._store.getStoredObject(transaction, parentPath);
		if (parentPath != null && parentSo != null && parentSo.isFolder())
		{
			so = this._store.getStoredObject(transaction, path);
			if (so == null)
			{
				this._store.createFolder(transaction, path);
				this._store.getResourceACL(transaction, path);
				resp.setStatus(CalDAVResponse.SC_CREATED);
			}
			else
			{
				if (so.isNullResource())
				{
					LockedObject nullResourceLo = this._resource_locks.getLockedObjectByPath(transaction, path);
					if (nullResourceLo == null)
					{
						resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
						return;
					}
					String nullResourceLockToken = nullResourceLo.getID();
					String[] lockTokens = getLockIdFromIfHeader(req);
					String lockToken = null;
					if (lockTokens != null)
						lockToken = lockTokens[0];
					else
					{
						resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
						return;
					}
					if (lockToken.equals(nullResourceLockToken))
					{
						so.setNullResource(false);
						so.setFolder(true);

						String[] nullResourceLockOwners = nullResourceLo.getOwner();
						String owner = null;
						if (nullResourceLockOwners != null)
							owner = nullResourceLockOwners[0];

						if (this._resource_locks.unlock(transaction, lockToken, owner))
						{
							resp.setStatus(CalDAVResponse.SC_CREATED);
						}
						else
						{
							resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
						}
					}
					else
					{
						// TODO remove
						errorList.put(path, CalDAVResponse.SC_LOCKED);
						sendReport(req, resp, errorList);
					}
				}
				else
				{
					String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
					resp.addHeader("Allow", methodsAllowed);
					resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
				}
			}
		}
		else if (parentPath != null && parentSo != null && parentSo.isResource())
		{
			// TODO remove
			String methodsAllowed = CalDAVMethods.determineMethodsAllowed(parentSo);
			resp.addHeader("Allow", methodsAllowed);
			resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
		}
		else if (parentPath != null && parentSo == null)
		{
			errorList.put(parentPath, CalDAVResponse.SC_NOT_FOUND);
			sendReport(req, resp, errorList);
		}
		else
		{
			resp.sendError(CalDAVResponse.SC_FORBIDDEN);
		}
	}
}
