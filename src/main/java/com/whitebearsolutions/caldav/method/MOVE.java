package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.ObjectAlreadyExistsException;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;

public class MOVE extends CalDAVAbstractMethod
{
	private ResourceLocksMap _resource_locks;
	private DELETE _delete;
	private COPY _copy;

	public MOVE(ResourceLocksMap resourceLocks, DELETE delete, COPY copy)
	{
		this._resource_locks = resourceLocks;
		this._delete = delete;
		this._copy = copy;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String sourcePath = getRelativePath(req);
		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

		if (!checkLocks(transaction, req, resp, this._resource_locks, sourcePath))
		{
			errorList.put(sourcePath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return;
		}

		String destinationPath = req.getHeader("Destination");
		if (destinationPath == null)
		{
			resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
			return;
		}

		if (!checkLocks(transaction, req, resp, this._resource_locks, destinationPath))
		{
			errorList.put(destinationPath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return;
		}

		String tempLockOwner = "MOVE" + System.currentTimeMillis() + req.toString();

		if (this._resource_locks.lock(transaction, sourcePath, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				if (this._copy.copyResource(transaction, req, resp))
				{
					errorList = new Hashtable<String, Integer>();
					this._delete.deleteResource(transaction, sourcePath, errorList, req, resp);
					if (!errorList.isEmpty())
					{
						sendReport(req, resp, errorList);
					}
				}
			}
			catch (AccessDeniedException _ex)
			{
				resp.sendError(CalDAVResponse.SC_FORBIDDEN);
			}
			catch (ObjectAlreadyExistsException _ex)
			{
				resp.sendError(CalDAVResponse.SC_NOT_FOUND, req.getRequestURI());
			}
			catch (CalDAVException _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			}
			finally
			{
				this._resource_locks.unlockTemporaryLockedObjects(transaction, sourcePath, tempLockOwner);
			}
		}
		else
		{
			errorList.put(req.getHeader("Destination"), CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
		}
	}
}
