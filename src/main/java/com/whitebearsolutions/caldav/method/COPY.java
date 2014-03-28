package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.ObjectAlreadyExistsException;
import com.whitebearsolutions.caldav.ObjectNotFoundException;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.security.acl.CalDAVPrivilegeCollection;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;

public class COPY extends CalDAVAbstractMethod
{
	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;
	private CalDAVResourceACL resource_acl;
	private DELETE _delete;

	public COPY(CalDAVStore store, ResourceLocksMap resourceLocks, DELETE delete)
	{
		this._store = store;
		this._resource_locks = resourceLocks;
		this._delete = delete;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getRelativePath(req);
		String tempLockOwner = "COPY" + System.currentTimeMillis() + req.toString();
		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				if (!copyResource(transaction, req, resp))
				{
					return;
				}
			}
			catch (AccessDeniedException _ex)
			{
				sendPrivilegeError(resp, path, _ex.getMessage());
			}
			catch (ObjectAlreadyExistsException _ex)
			{
				resp.sendError(CalDAVResponse.SC_CONFLICT, req.getRequestURI());
			}
			catch (ObjectNotFoundException _es)
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
				this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
			}
		}
		else
		{
			logger.error("Unable to lock the resource: {}", path);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Copy a resource.
	 * 
	 * @param transaction
	 *            indicates that the method is within the scope of a WebDAV
	 *            transaction
	 * @param req
	 *            Servlet request
	 * @param resp
	 *            Servlet response
	 * @return true if the copy is successful
	 * @throws WebdavException
	 *             if an error in the underlying store occurs
	 * @throws IOException
	 *             when an error occurs while sending the response
	 * @throws LockFailedException
	 */
	public boolean copyResource(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws CalDAVException, IOException, LockException, AccessDeniedException
	{

		// Parsing destination header
		String destinationPath = parseDestinationHeader(req, resp);

		if (destinationPath == null)
		{
			return false;
		}

		String path = getRelativePath(req);

		if (path.equals(destinationPath))
		{
			resp.sendError(CalDAVResponse.SC_FORBIDDEN);
			return false;
		}

		/*
		 * Verifying permissions
		 */
		this.resource_acl = this._store.getResourceACL(transaction, path);
		CalDAVPrivilegeCollection collection = this.resource_acl.getPrivilegeCollection();
		collection.checkPrincipalPrivilege(req.getUserPrincipal(), "read");
		CalDAVResourceACL resource = this._store.getResourceACL(transaction, destinationPath);
		resource.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(), "write");

		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
		String parentDestinationPath = getParentPath(getCleanPath(destinationPath));

		if (!checkLocks(transaction, req, resp, this._resource_locks, parentDestinationPath))
		{
			errorList.put(parentDestinationPath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return false; // parentDestination is locked
		}

		if (!checkLocks(transaction, req, resp, this._resource_locks, destinationPath))
		{
			errorList.put(destinationPath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return false; // destination is locked
		}

		// Parsing overwrite header
		boolean overwrite = true;
		String overwriteHeader = req.getHeader("Overwrite");

		if (overwriteHeader != null)
		{
			overwrite = overwriteHeader.equalsIgnoreCase("T");
		}

		String lockOwner = "COPY" + System.currentTimeMillis() + req.toString();

		if (this._resource_locks.lock(transaction, destinationPath, lockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			StoredObject copySo, destinationSo = null;
			try
			{
				copySo = this._store.getStoredObject(transaction, path);
				if (copySo == null)
				{
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return false;
				}

				if (copySo.isNullResource())
				{
					String methodsAllowed = CalDAVMethods.determineMethodsAllowed(copySo);
					resp.addHeader("Allow", methodsAllowed);
					resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
					return false;
				}

				errorList = new Hashtable<String, Integer>();
				destinationSo = this._store.getStoredObject(transaction, destinationPath);

				if (overwrite)
				{
					if (destinationSo != null)
					{
						resource.getPrivilegeCollection().checkPrincipalPrivilege(req.getUserPrincipal(), "write");
						this._delete.deleteResource(transaction, destinationPath, errorList, req, resp);
						resource.removeCollection(transaction);
					}
					else
					{
						resp.setStatus(CalDAVResponse.SC_CREATED);
					}
				}
				else
				{
					if (destinationSo != null)
					{
						resp.sendError(CalDAVResponse.SC_PRECONDITION_FAILED);
						return false;
					}
					else
					{
						resp.setStatus(CalDAVResponse.SC_CREATED);
					}
				}
				copy(transaction, path, destinationPath, errorList, req, resp);
				resource.setPrivilegeCollection(transaction, collection);

				if (!errorList.isEmpty())
				{
					sendReport(req, resp, errorList);
				}
			}
			finally
			{
				this._resource_locks.unlockTemporaryLockedObjects(transaction, destinationPath, lockOwner);
			}
		}
		else
		{
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			return false;
		}
		return true;
	}

	/**
	 * copies the specified resource(s) to the specified destination.
	 * preconditions must be handled by the caller. Standard status codes must
	 * be handled by the caller. a multi status report in case of errors is
	 * created here.
	 * 
	 * @param transaction
	 *            indicates that the method is within the scope of a WebDAV
	 *            transaction
	 * @param sourcePath
	 *            path from where to read
	 * @param destinationPath
	 *            path where to write
	 * @param req
	 *            HttpServletRequest
	 * @param resp
	 *            HttpServletResponse
	 * @throws WebdavException
	 *             if an error in the underlying store occurs
	 * @throws IOException
	 */
	private void copy(CalDAVTransaction transaction, String sourcePath, String destinationPath,
			Hashtable<String, Integer> errorList, HttpServletRequest req, HttpServletResponse resp)
			throws CalDAVException, IOException
	{

		StoredObject sourceSo = this._store.getStoredObject(transaction, sourcePath);
		if (sourceSo.isResource())
		{
			this._store.createResource(transaction, destinationPath);
			long resourceLength = this._store.setResourceContent(transaction, destinationPath,
					this._store.getResourceContent(transaction, sourcePath), null, null);

			if (resourceLength != -1)
			{
				StoredObject destinationSo = this._store.getStoredObject(transaction, destinationPath);
				destinationSo.setResourceLength(resourceLength);
			}
		}
		else
		{
			if (sourceSo.isFolder())
			{
				copyFolder(transaction, sourcePath, destinationPath, errorList, req, resp);
			}
			else
			{
				resp.sendError(CalDAVResponse.SC_NOT_FOUND);
			}
		}
	}

	/**
	 * helper method of copy() recursively copies the FOLDER at source path to
	 * destination path
	 * 
	 * @param transaction
	 *            indicates that the method is within the scope of a WebDAV
	 *            transaction
	 * @param sourcePath
	 *            where to read
	 * @param destinationPath
	 *            where to write
	 * @param errorList
	 *            all errors that ocurred
	 * @param req
	 *            HttpServletRequest
	 * @param resp
	 *            HttpServletResponse
	 * @throws WebdavException
	 *             if an error in the underlying store occurs
	 */
	private void copyFolder(CalDAVTransaction transaction, String sourcePath, String destinationPath,
			Hashtable<String, Integer> errorList, HttpServletRequest req, HttpServletResponse resp)
			throws CalDAVException
	{

		this._store.createFolder(transaction, destinationPath);
		boolean infiniteDepth = true;
		String depth = req.getHeader("Depth");
		if (depth != null)
		{
			if (depth.equals("0"))
			{
				infiniteDepth = false;
			}
		}
		if (infiniteDepth)
		{
			String[] children = this._store.getChildrenNames(transaction, sourcePath);

			StoredObject childSo;
			for (int i = children.length - 1; i >= 0; i--)
			{
				children[i] = "/" + children[i];
				try
				{
					childSo = this._store.getStoredObject(transaction, (sourcePath + children[i]));
					if (childSo.isResource())
					{
						this._store.createResource(transaction, destinationPath + children[i]);
						long resourceLength = this._store.setResourceContent(transaction,
								destinationPath + children[i],
								this._store.getResourceContent(transaction, sourcePath + children[i]), null, null);

						if (resourceLength != -1)
						{
							StoredObject destinationSo = this._store.getStoredObject(transaction, destinationPath
									+ children[i]);
							destinationSo.setResourceLength(resourceLength);
						}
					}
					else
					{
						copyFolder(transaction, sourcePath + children[i], destinationPath + children[i], errorList,
								req, resp);
					}
				}
				catch (AccessDeniedException _ex)
				{
					errorList.put(destinationPath + children[i], new Integer(CalDAVResponse.SC_FORBIDDEN));
				}
				catch (ObjectNotFoundException _ex)
				{
					errorList.put(destinationPath + children[i], new Integer(CalDAVResponse.SC_NOT_FOUND));
				}
				catch (ObjectAlreadyExistsException _ex)
				{
					errorList.put(destinationPath + children[i], new Integer(CalDAVResponse.SC_CONFLICT));
				}
				catch (CalDAVException _ex)
				{
					errorList.put(destinationPath + children[i], new Integer(CalDAVResponse.SC_INTERNAL_SERVER_ERROR));
				}
			}
		}
	}

	/**
	 * Parses and normalizes the destination header.
	 * 
	 * @param req
	 *            Servlet request
	 * @param resp
	 *            Servlet response
	 * @return destinationPath
	 * @throws IOException
	 *             if an error occurs while sending response
	 */
	private String parseDestinationHeader(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		String destinationPath = req.getHeader("Destination");

		if (destinationPath == null)
		{
			resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
			return null;
		}

		// Remove url encoding from destination
		destinationPath = com.whitebearsolutions.xml.HTTPRequestUtil.URLDecode(destinationPath, "UTF8");

		int protocolIndex = destinationPath.indexOf("://");
		if (protocolIndex >= 0)
		{
			// if the Destination URL contains the protocol, we can safely
			// trim everything upto the first "/" character after "://"
			int firstSeparator = destinationPath.indexOf("/", protocolIndex + 4);
			if (firstSeparator < 0)
			{
				destinationPath = "/";
			}
			else
			{
				destinationPath = destinationPath.substring(firstSeparator);
			}
		}
		else
		{
			String hostName = req.getServerName();
			if ((hostName != null) && (destinationPath.startsWith(hostName)))
			{
				destinationPath = destinationPath.substring(hostName.length());
			}

			int portIndex = destinationPath.indexOf(":");
			if (portIndex >= 0)
			{
				destinationPath = destinationPath.substring(portIndex);
			}

			if (destinationPath.startsWith(":"))
			{
				int firstSeparator = destinationPath.indexOf("/");
				if (firstSeparator < 0)
				{
					destinationPath = "/";
				}
				else
				{
					destinationPath = destinationPath.substring(firstSeparator);
				}
			}
		}

		// Normalize destination path (remove '.' and' ..')
		destinationPath = normalize(destinationPath);

		String contextPath = req.getContextPath();
		if ((contextPath != null) && (destinationPath.startsWith(contextPath)))
		{
			destinationPath = destinationPath.substring(contextPath.length());
		}

		String pathInfo = req.getPathInfo();
		if (pathInfo != null)
		{
			String servletPath = req.getServletPath();
			if ((servletPath != null) && (destinationPath.startsWith(servletPath)))
			{
				destinationPath = destinationPath.substring(servletPath.length());
			}
		}

		return destinationPath;
	}

	/**
	 * Return a context-relative path, beginning with a "/", that represents the
	 * canonical version of the specified path after ".." and "." elements are
	 * resolved out. If the specified path attempts to go outside the boundaries
	 * of the current context (i.e. too many ".." path elements are present),
	 * return <code>null</code> instead.
	 * 
	 * @param path
	 *            Path to be normalized
	 * @return normalized path
	 */
	protected String normalize(String path)
	{

		if (path == null)
			return null;

		// Create a place for the normalized path
		String normalized = path;

		if (normalized.equals("/."))
		{
			return "/";
		}

		// Normalize the slashes and add leading slash if necessary
		if (normalized.indexOf('\\') >= 0)
		{
			normalized = normalized.replace('\\', '/');
		}
		if (!normalized.startsWith("/"))
		{
			normalized = "/" + normalized;
		}

		// Resolve occurrences of "//" in the normalized path
		while (true)
		{
			int index = normalized.indexOf("//");
			if (index < 0)
			{
				break;
			}
			normalized = normalized.substring(0, index) + normalized.substring(index + 1);
		}

		// Resolve occurrences of "/./" in the normalized path
		while (true)
		{
			int index = normalized.indexOf("/./");
			if (index < 0)
			{
				break;
			}
			normalized = normalized.substring(0, index) + normalized.substring(index + 2);
		}

		// Resolve occurrences of "/../" in the normalized path
		while (true)
		{
			int index = normalized.indexOf("/../");
			if (index < 0)
			{
				break;
			}
			if (index == 0)
			{
				return (null); // Trying to go outside our context
			}
			int index2 = normalized.lastIndexOf('/', index - 1);
			normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
		}

		// Return the normalized path that we have completed
		return (normalized);
	}

}