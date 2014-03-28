package com.whitebearsolutions.caldav.method;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.CalDAVServlet;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.LockedObject;
import com.whitebearsolutions.caldav.locking.ResourceLocks;
import com.whitebearsolutions.caldav.security.acl.CalDAVPrivilegeCollection;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.VCalendar;
import com.whitebearsolutions.xml.VCalendarCache;
import com.whitebearsolutions.xml.VEvent;
import com.whitebearsolutions.xml.VTodo;

public class PUT extends CalDAVAbstractMethod
{
	private CalDAVStore _store;
	private CalDAVResourceACL resource_acl;
	private ResourceLocks _resource_locks;
	private boolean _lazyFolderCreationOnPut;

	private String _userAgent;

	public PUT(CalDAVStore store, ResourceLocks resLocks, boolean lazyFolderCreationOnPut)
	{
		this._store = store;
		this._resource_locks = resLocks;
		this._lazyFolderCreationOnPut = lazyFolderCreationOnPut;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getRelativePath(req);
		String parentPath = getParentPath(path);

		this._userAgent = req.getHeader("User-Agent");
		this.resource_acl = this._store.getResourceACL(transaction, parentPath);

		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

		if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath))
		{
			sendReport(req, resp, errorList);
			return; // parent is locked
		}

		if (!checkLocks(transaction, req, resp, this._resource_locks, path))
		{
			errorList.put(path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return; // resource is locked
		}

		String tempLockOwner = "PUT" + System.currentTimeMillis() + req.toString();
		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			StoredObject parentSo, so = null;
			try
			{
				CalDAVPrivilegeCollection collection = this.resource_acl.getPrivilegeCollection();
				collection.checkPrincipalPrivilege(CalDAVServlet.provider.getUserPrincipal(req), "write");

				parentSo = this._store.getStoredObject(transaction, parentPath);
				if (parentPath != null && parentSo != null && parentSo.isResource())
				{
					resp.sendError(CalDAVResponse.SC_FORBIDDEN);
					return;
				}
				else if (parentPath != null && parentSo == null && parentPath.endsWith(".ics"))
				{
					/*
					 * Allow updates for sunbird
					 */
					while (parentPath.endsWith(".ics"))
					{
						parentPath = parentPath.substring(0, parentPath.lastIndexOf("/"));
					}
					path = parentPath + path.substring(path.lastIndexOf("/"));
				}
				else if (parentPath != null && parentSo == null && this._lazyFolderCreationOnPut)
				{
					this._store.createFolder(transaction, parentPath);
				}
				else if (parentPath != null && parentSo == null && !this._lazyFolderCreationOnPut)
				{
					errorList.put(parentPath, CalDAVResponse.SC_NOT_FOUND);
					sendReport(req, resp, errorList);
					return;
				}

				so = this._store.getStoredObject(transaction, path);
				if (!path.endsWith(".ics"))
				{
					if (so == null)
					{
						this._store.createResource(transaction, path);
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
							{
								lockToken = lockTokens[0];
							}
							else
							{
								resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
								return;
							}
							if (lockToken.equals(nullResourceLockToken))
							{
								so.setNullResource(false);
								so.setFolder(false);

								String[] nullResourceLockOwners = nullResourceLo.getOwner();
								String owner = null;
								if (nullResourceLockOwners != null)
								{
									owner = nullResourceLockOwners[0];
								}
								if (!this._resource_locks.unlock(transaction, lockToken, owner))
								{
									resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
								}
							}
							else
							{
								errorList.put(path, CalDAVResponse.SC_LOCKED);
								sendReport(req, resp, errorList);
							}
						}
					}
				}

				// User-Agent workarounds
				doUserAgentWorkaround(resp);

				long length = -1;
				if (path.endsWith(".ics"))
				{
					String calendarPath = parentPath.concat("/calendar.ics");
					File _f = new File(this._store.getRootPath() + calendarPath);
					InputStream is = req.getInputStream();
					ByteArrayOutputStream _baos = new ByteArrayOutputStream();
					for (int i = is.read(); i != -1; i = is.read())
					{
						_baos.write(i);
					}
					byte[] buffer = _baos.toByteArray();
					_baos.close();

					VCalendar _vc = VCalendarCache.getVCalendar(_f);
					VCalendar _req_vc = CalDAVServlet.provider.createCalendar(new ByteArrayInputStream(buffer));
					for (VEvent ve : _req_vc.getVevents())
					{
						if (!ve.hasLastModified())
						{
							ve.setLastModified(Calendar.getInstance());
						}
						_vc.addVevent(ve);
					}
					for (VTodo vt : _req_vc.getVtodos())
					{
						if (!vt.hasLastModified())
						{
							vt.setLastModified(Calendar.getInstance());
						}
						_vc.addVtodo(vt);
					}

					VCalendarCache.putVCalendar(_vc, _f);
					this._store.setResourceContent(transaction, calendarPath, new ByteArrayInputStream(_vc.toString()
							.getBytes()), "text/calendar", null);
				}
				else
				{
					length = this._store.setResourceContent(transaction, path, req.getInputStream(), null, null);
				}

				so = this._store.getStoredObject(transaction, path);
				if (length != -1)
				{
					so.setResourceLength(length);
				}

				this._store.getResourceACL(transaction, path);

				// Now lets report back what was actually saved
				// Webdav Client Goliath executes 2 PUTs with the same
				// resource when contentLength is added to response
				if (this._userAgent != null && this._userAgent.indexOf("Goliath") != -1)
				{
					// nada
				}
				else
				{
					resp.setContentLength((int) length);
				}
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
			logger.error("Unable to lock resource: {}", path);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @param resp
	 */
	private void doUserAgentWorkaround(HttpServletResponse resp)
	{
		if (this._userAgent != null && this._userAgent.indexOf("WebDAVFS") != -1
				&& _userAgent.indexOf("Transmit") == -1)
		{
			resp.setStatus(CalDAVResponse.SC_CREATED);
		}
		else if (this._userAgent != null && this._userAgent.indexOf("Transmit") != -1)
		{
			// Transmit also uses WEBDAVFS 1.x.x but crashes
			// with SC_CREATED response
			resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
		}
		else
		{
			resp.setStatus(CalDAVResponse.SC_CREATED);
		}
	}
}