package com.whitebearsolutions.caldav.method;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.ObjectAlreadyExistsException;
import com.whitebearsolutions.caldav.ObjectNotFoundException;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.VCalendar;
import com.whitebearsolutions.xml.VCalendarCache;

public class DELETE extends CalDAVAbstractMethod
{
	Logger logger = LogManager.getLogger();
	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;
	private CalDAVResourceACL resource_acl;

	public DELETE(CalDAVStore store, ResourceLocksMap resourceLocks)
	{
		this._store = store;
		this._resource_locks = resourceLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{

		String path = getRelativePath(req);
		String parentPath = getParentPath(getCleanPath(path));
		/*
		 * Permite que funcionen las actualizaciones de SunBird
		 */
		if (parentPath.endsWith(".ics"))
		{
			while (parentPath.endsWith(".ics"))
			{
				parentPath = parentPath.substring(0, parentPath.lastIndexOf("/"));
			}
			path = parentPath + path.substring(path.lastIndexOf("/"));
		}

		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

		if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath))
		{
			errorList.put(parentPath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return;
		}

		if (!checkLocks(transaction, req, resp, this._resource_locks, path))
		{
			errorList.put(path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return;
		}

		String tempLockOwner = "doDelete" + System.currentTimeMillis() + req.toString();
		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				errorList = new Hashtable<String, Integer>();

				if (path.endsWith(".ics"))
				{
					StoredObject so = this._store.getStoredObject(transaction, path);
					if (so == null)
					{
						String uid = path.substring(path.lastIndexOf("/") + 1);
						if (uid.endsWith(".ics"))
						{
							uid = uid.substring(0, uid.length() - 4);
						}

						String href = path.substring(0, path.lastIndexOf("/"));
						if (href.endsWith(".ics"))
						{
							href = href.substring(0, href.lastIndexOf("/"));
						}
						href = href.concat("/calendar.ics");

						this.resource_acl = this._store.getResourceACL(transaction, href);
						this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(),
								"write");

						File _f = new File(this._store.getRootPath() + href);
						VCalendar _vc = VCalendarCache.getVCalendar(_f);
						_vc.removeVevent(uid);
						_vc.removeVtodo(uid);
						VCalendarCache.putVCalendar(_vc, _f);
						this._store.setResourceContent(transaction, href, new ByteArrayInputStream(_vc.toString()
								.getBytes()), "text/calendar", null);
						resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
					}
					else
					{
						deleteResource(transaction, path, errorList, req, resp);
					}
				}
				else
				{
					deleteResource(transaction, path, errorList, req, resp);
				}

				if (!errorList.isEmpty())
				{
					sendReport(req, resp, errorList);
				}
			}
			catch (AccessDeniedException _ex)
			{
				sendPrivilegeError(resp, path, _ex.getMessage());
			}
			catch (ObjectAlreadyExistsException _ex)
			{
				resp.sendError(CalDAVResponse.SC_NOT_FOUND, req.getRequestURI());
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

	/**
	 * Elimina los recursos en la ruta "path"
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param path
	 *            la carpeta a eliminar
	 * @param errorList
	 *            la lista de errores ocurridos
	 * @param req
	 *            HttpServletRequest
	 * @param resp
	 *            HttpServletResponse
	 * @throws CalDAVException
	 * @throws IOException
	 */
	public void deleteResource(CalDAVTransaction transaction, String path, Hashtable<String, Integer> errorList,
			HttpServletRequest req, HttpServletResponse resp) throws IOException, CalDAVException
	{
		this.resource_acl = this._store.getResourceACL(transaction, path);
		this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(), "write");

		resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
		StoredObject so = this._store.getStoredObject(transaction, path);
		if (so != null)
		{
			if (so.isResource())
			{
				this._store.removeObject(transaction, path);
				this.resource_acl.removeCollection(transaction);
			}
			else
			{
				if (so.isFolder())
				{
					deleteFolder(transaction, path, errorList, req, resp);
					this._store.removeObject(transaction, path);
				}
				else
				{
					resp.sendError(CalDAVResponse.SC_NOT_FOUND);
				}
			}
		}
		else
		{
			resp.sendError(CalDAVResponse.SC_NOT_FOUND);
		}
		so = null;
	}

	/**
	 * 
	 * Metodo auxiliar para deleteResource() que elimina la carpeta y todo su
	 * contenido
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param path
	 *            la carpeta a eliminar
	 * @param errorList
	 *            la lista de errores ocurridos
	 * @param req
	 *            HttpServletRequest
	 * @param resp
	 *            HttpServletResponse
	 * @throws CalDAVExceptions
	 */
	private void deleteFolder(CalDAVTransaction transaction, String path, Hashtable<String, Integer> errorList,
			HttpServletRequest req, HttpServletReloggersponse resp) throws IOException, CalDAVException
	{
		List<String> _childrens = Arrays.asList(this._store.getAllChildrenNames(transaction, path));
		StoredObject so = null;
		for (String children : _childrens)
		{
			try
			{
				so = this._store.getStoredObject(transaction, path + children);
				if (so.isResource())
				{
					this._store.removeObject(transaction, path + children);
				}
				else
				{
					deleteFolder(transaction, path + children, errorList, req, resp);
					this._store.removeObject(transaction, path + children);
				}
			}
			catch (AccessDeniedException _ex)
			{
				sendPrivilegeError(resp, path, _ex.getMessage());
			}
			catch (ObjectNotFoundException _ex)
			{
				errorList.put(path + children, new Integer(CalDAVResponse.SC_NOT_FOUND));
			}
			catch (Exception _ex)
			{
				logger.error(_ex);
				errorList.put(path + children, new Integer(CalDAVResponse.SC_INTERNAL_SERVER_ERROR));
			}
		}
		so = null;
	}

}