package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.util.Hashtable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.LockedObject;
import com.whitebearsolutions.caldav.locking.ResourceLocks;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.XMLWriter;

public class LOCK extends CalDAVAbstractMethod
{
	Logger logger = LogManager.getLogger();

	private CalDAVStore _store;
	private ResourceLocks _resource_locks;

	private boolean _macLockRequest = false;

	private boolean _exclusive = false;
	private String _type = null;
	private String _lockOwner = null;

	private String _path = null;
	private String _parentPath = null;

	private String _userAgent = null;

	public LOCK(CalDAVStore store, ResourceLocks resourceLocks)
	{
		this._store = store;
		this._resource_locks = resourceLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{

		this._path = getRelativePath(req);
		this._parentPath = getParentPath(getCleanPath(this._path));

		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

		if (!checkLocks(transaction, req, resp, this._resource_locks, _path))
		{
			errorList.put(this._path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return; // resource is locked
		}

		if (!checkLocks(transaction, req, resp, this._resource_locks, this._parentPath))
		{
			errorList.put(this._parentPath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return; // parent is locked
		}

		// Mac OS Finder (whether 10.4.x or 10.5) can't store files
		// because executing a LOCK without lock information causes a
		// SC_BAD_REQUEST
		this._userAgent = req.getHeader("User-Agent");
		if (this._userAgent != null && this._userAgent.indexOf("Darwin") != -1)
		{
			this._macLockRequest = true;

			String timeString = new Long(System.currentTimeMillis()).toString();
			this._lockOwner = this._userAgent.concat(timeString);
		}

		String tempLockOwner = "LOCK" + System.currentTimeMillis() + req.toString();
		if (this._resource_locks.lock(transaction, _path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				if (req.getHeader("If") != null)
				{
					doRefreshLock(transaction, req, resp);
				}
				else
				{
					doLock(transaction, req, resp);
				}
			}
			catch (LockException _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_LOCKED);
			}
			finally
			{
				this._resource_locks.unlockTemporaryLockedObjects(transaction, this._path, tempLockOwner);
			}
		}
	}

	private void doLock(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{

		StoredObject so = _store.getStoredObject(transaction, _path);

		if (so != null)
		{
			doLocking(transaction, req, resp);
		}
		else
		{
			// resource doesn't exist, null-resource lock
			doNullResourceLock(transaction, req, resp);
		}

		so = null;
		this._exclusive = false;
		this._type = null;
		this._lockOwner = null;
	}

	private void doLocking(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException
	{

		// Tests if LockObject on requested path exists, and if so, tests
		// exclusivity
		LockedObject lo = this._resource_locks.getLockedObjectByPath(transaction, this._path);
		if (lo != null)
		{
			if (lo.isExclusive())
			{
				sendLockFailError(transaction, req, resp);
				return;
			}
		}
		try
		{
			// Thats the locking itself
			executeLock(transaction, req, resp);
		}
		catch (ServletException _ex)
		{
			logger.error(_ex);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
		catch (LockException _ex)
		{
			sendLockFailError(transaction, req, resp);
		}
		finally
		{
			lo = null;
		}
	}

	private void doNullResourceLock(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException
	{

		StoredObject parentSo, nullSo = null;
		try
		{
			parentSo = this._store.getStoredObject(transaction, this._parentPath);
			if (this._parentPath != null && parentSo == null)
			{
				this._store.createFolder(transaction, this._parentPath);
			}
			else if (this._parentPath != null && parentSo != null && parentSo.isResource())
			{
				resp.sendError(CalDAVResponse.SC_PRECONDITION_FAILED);
				return;
			}

			nullSo = this._store.getStoredObject(transaction, this._path);
			if (nullSo == null)
			{
				// resource doesn't exist
				this._store.createResource(transaction, this._path);

				// Transmit expects 204 response-code, not 201
				if (this._userAgent != null && this._userAgent.indexOf("Transmit") != -1)
				{
					resp.setStatus(CalDAVResponse.SC_NO_CONTENT);
				}
				else
				{
					resp.setStatus(CalDAVResponse.SC_CREATED);
				}
			}
			else
			{
				// resource already exists, could not execute null-resource lock
				sendLockFailError(transaction, req, resp);
				return;
			}
			nullSo = this._store.getStoredObject(transaction, this._path);
			// define the newly created resource as null-resource
			nullSo.setNullResource(true);

			// Thats the locking itself
			executeLock(transaction, req, resp);
		}
		catch (LockException _ex)
		{
			sendLockFailError(transaction, req, resp);
		}
		catch (CalDAVException _ex)
		{
			logger.error(_ex);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
		catch (ServletException _ex)
		{
			logger.error(_ex);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
		finally
		{
			parentSo = null;
			nullSo = null;
		}
	}

	private void doRefreshLock(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{

		String[] lockTokens = getLockIdFromIfHeader(req);
		String lockToken = null;
		if (lockTokens != null)
		{
			lockToken = lockTokens[0];
		}

		if (lockToken != null)
		{
			// Getting LockObject of specified lockToken in If header
			LockedObject refreshLo = this._resource_locks.getLockedObjectByID(transaction, lockToken);
			if (refreshLo != null)
			{
				int timeout = getTimeout(transaction, req);

				refreshLo.refreshTimeout(timeout);
				// sending success response
				generateXMLReport(transaction, resp, refreshLo);

				refreshLo = null;
			}
			else
			{
				// no LockObject to given lockToken
				resp.sendError(CalDAVResponse.SC_PRECONDITION_FAILED);
			}
		}
		else
		{
			resp.sendError(CalDAVResponse.SC_PRECONDITION_FAILED);
		}
	}

	// ------------------------------------------------- helper methods

	/**
	 * Executes the LOCK
	 */
	private void executeLock(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws LockException, IOException, ServletException
	{

		// Mac OS lock request workaround
		if (this._macLockRequest)
		{
			// Workaround for Mac OS
			doMacLockRequestWorkaround(transaction, req, resp);
		}
		else
		{
			// Getting LockInformation from request
			if (getLockInformation(transaction, req, resp))
			{
				int depth = getDepth(req);
				int lockDuration = getTimeout(transaction, req);

				boolean lockSuccess = false;
				if (this._exclusive)
				{
					lockSuccess = this._resource_locks.exclusiveLock(transaction, this._path, this._lockOwner, depth,
							lockDuration);
				}
				else
				{
					lockSuccess = this._resource_locks.sharedLock(transaction, this._path, this._lockOwner, depth,
							lockDuration);
				}

				if (lockSuccess)
				{
					// Locks successfully placed - return information about
					LockedObject lo = this._resource_locks.getLockedObjectByPath(transaction, this._path);
					if (lo != null)
					{
						generateXMLReport(transaction, resp, lo);
					}
					else
					{
						resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
					}
				}
				else
				{
					sendLockFailError(transaction, req, resp);
					throw new LockException();
				}
			}
			else
			{
				// information for LOCK could not be read successfully
				resp.setContentType("text/xml; charset=UTF-8");
				resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
			}
		}
	}

	/**
	 * Tries to get the LockInformation from LOCK request
	 */
	private boolean getLockInformation(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException
	{

		Node lockInfoNode = null;
		DocumentBuilder documentBuilder = null;

		documentBuilder = getDocumentBuilder();
		try
		{
			Document document = documentBuilder.parse(new InputSource(req.getInputStream()));

			// Get the root element of the document
			Element rootElement = document.getDocumentElement();
			lockInfoNode = rootElement;

			if (lockInfoNode != null)
			{
				NodeList childList = lockInfoNode.getChildNodes();
				Node lockScopeNode = null;
				Node lockTypeNode = null;
				Node lockOwnerNode = null;

				Node currentNode = null;
				String nodeName = null;

				for (int i = 0; i < childList.getLength(); i++)
				{
					currentNode = childList.item(i);

					if (currentNode.getNodeType() == Node.ELEMENT_NODE || currentNode.getNodeType() == Node.TEXT_NODE)
					{
						nodeName = currentNode.getNodeName();

						if (nodeName.endsWith("locktype"))
						{
							lockTypeNode = currentNode;
						}
						if (nodeName.endsWith("lockscope"))
						{
							lockScopeNode = currentNode;
						}
						if (nodeName.endsWith("owner"))
						{
							lockOwnerNode = currentNode;
						}
					}
					else
					{
						return false;
					}
				}

				if (lockScopeNode != null)
				{
					String scope = null;
					childList = lockScopeNode.getChildNodes();
					for (int i = 0; i < childList.getLength(); i++)
					{
						currentNode = childList.item(i);

						if (currentNode.getNodeType() == Node.ELEMENT_NODE)
						{
							scope = currentNode.getNodeName();

							if (scope.endsWith("exclusive"))
							{
								this._exclusive = true;
							}
							else if (scope.equals("shared"))
							{
								this._exclusive = false;
							}
						}
					}
					if (scope == null)
					{
						return false;
					}

				}
				else
				{
					return false;
				}

				if (lockTypeNode != null)
				{
					childList = lockTypeNode.getChildNodes();
					for (int i = 0; i < childList.getLength(); i++)
					{
						currentNode = childList.item(i);

						if (currentNode.getNodeType() == Node.ELEMENT_NODE)
						{
							this._type = currentNode.getNodeName();

							if (this._type.endsWith("write"))
							{
								this._type = "write";
							}
							else if (this._type.equals("read"))
							{
								this._type = "read";
							}
						}
					}
					if (this._type == null)
					{
						return false;
					}
				}
				else
				{
					return false;
				}

				if (lockOwnerNode != null)
				{
					childList = lockOwnerNode.getChildNodes();
					for (int i = 0; i < childList.getLength(); i++)
					{
						currentNode = childList.item(i);

						if (currentNode.getNodeType() == Node.ELEMENT_NODE)
						{
							this._lockOwner = currentNode.getTextContent();
						}
					}
				}
				if (this._lockOwner == null)
				{
					return false;
				}
			}
			else
			{
				return false;
			}
		}
		catch (DOMException _ex)
		{
			logger.error(_ex);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			return false;
		}
		catch (SAXException _ex)
		{
			logger.error(_ex);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			return false;
		}
		return true;
	}

	/**
	 * Ties to read the timeout from request
	 */
	private int getTimeout(CalDAVTransaction transaction, HttpServletRequest req)
	{
		int lockDuration = DEFAULT_TIMEOUT;
		String lockDurationStr = req.getHeader("Timeout");

		if (lockDurationStr == null)
		{
			lockDuration = DEFAULT_TIMEOUT;
		}
		else
		{
			int commaPos = lockDurationStr.indexOf(',');
			// if multiple timeouts, just use the first one
			if (commaPos != -1)
			{
				lockDurationStr = lockDurationStr.substring(0, commaPos);
			}
			if (lockDurationStr.startsWith("Second-"))
			{
				lockDuration = new Integer(lockDurationStr.substring(7)).intValue();
			}
			else
			{
				if (lockDurationStr.equalsIgnoreCase("infinity"))
				{
					lockDuration = MAX_TIMEOUT;
				}
				else
				{
					try
					{
						lockDuration = new Integer(lockDurationStr).intValue();
					}
					catch (NumberFormatException _ex)
					{
						lockDuration = MAX_TIMEOUT;
					}
				}
			}
			if (lockDuration <= 0)
			{
				lockDuration = DEFAULT_TIMEOUT;
			}
			if (lockDuration > MAX_TIMEOUT)
			{
				lockDuration = MAX_TIMEOUT;
			}
		}
		return lockDuration;
	}

	/**
	 * Generates the response XML with all lock information
	 */
	private void generateXMLReport(CalDAVTransaction transaction, HttpServletResponse resp, LockedObject lo)
			throws IOException
	{

		resp.setStatus(CalDAVResponse.SC_OK);
		resp.setContentType("text/xml; charset=UTF-8");

		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:prop");
			XML.addChildElement("D:lockdiscovery");
			XML.addChildElement("D:activelock");

			XML.addChildElement("D:locktype");
			XML.addProperty("D:" + _type);
			XML.closeElement();

			XML.addChildElement("D:lockscope");
			if (this._exclusive)
			{
				XML.addProperty("D:exclusive");
			}
			else
			{
				XML.addProperty("D:shared");
			}
			XML.closeElement();

			int depth = lo.getLockDepth();

			XML.addChildElement("D:depth");
			if (depth == INFINITY)
			{
				XML.setTextContent("Infinity");
			}
			else
			{
				XML.setTextContent(String.valueOf(depth));
			}
			XML.closeElement();

			XML.addChildElement("D:owner");
			XML.addChildElement("D:href");
			XML.setTextContent(this._lockOwner);
			XML.closeElement();
			XML.closeElement();

			long timeout = lo.getTimeoutMillis();
			XML.addChildElement("D:timeout");
			XML.setTextContent("Second-" + timeout / 1000);
			XML.closeElement();

			String lockToken = lo.getID();
			XML.addChildElement("D:locktoken");
			XML.addChildElement("D:href");
			XML.setTextContent("opaquelocktoken:" + lockToken);
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			resp.addHeader("Lock-Token", "<opaquelocktoken:" + lockToken + ">");

			XML.write(resp.getOutputStream());
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Executes the lock for a Mac OS Finder client
	 */
	private void doMacLockRequestWorkaround(CalDAVTransaction transaction, HttpServletRequest req,
			HttpServletResponse resp) throws LockException, IOException
	{
		LockedObject lo;
		int depth = getDepth(req);
		int lockDuration = getTimeout(transaction, req);
		if (lockDuration < 0 || lockDuration > MAX_TIMEOUT)
			lockDuration = DEFAULT_TIMEOUT;

		boolean lockSuccess = false;
		lockSuccess = this._resource_locks.exclusiveLock(transaction, this._path, this._lockOwner, depth, lockDuration);

		if (lockSuccess)
		{
			// Locks successfully placed - return information about
			lo = this._resource_locks.getLockedObjectByPath(transaction, _path);
			if (lo != null)
			{
				generateXMLReport(transaction, resp, lo);
			}
			else
			{
				resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}
		else
		{
			// Locking was not successful
			sendLockFailError(transaction, req, resp);
		}
	}

	/**
	 * Sends an error report to the client
	 */
	private void sendLockFailError(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException
	{
		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
		errorList.put(this._path, CalDAVResponse.SC_LOCKED);
		sendReport(req, resp, errorList);
	}
}
