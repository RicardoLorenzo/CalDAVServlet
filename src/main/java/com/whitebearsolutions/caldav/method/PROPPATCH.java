package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.LockedObject;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.XMLHelper;
import com.whitebearsolutions.xml.XMLWriter;

public class PROPPATCH extends CalDAVAbstractMethod
{
	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;

	public PROPPATCH(CalDAVStore store, ResourceLocksMap resLocks)
	{
		this._store = store;
		this._resource_locks = resLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getRelativePath(req);
		String parentPath = getParentPath(getCleanPath(path));

		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

		if (!checkLocks(transaction, req, resp, this._resource_locks, parentPath))
		{
			errorList.put(parentPath, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return; // parent is locked
		}

		if (!checkLocks(transaction, req, resp, this._resource_locks, path))
		{
			errorList.put(path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
			return; // resource is locked
		}

		// TODO for now, PROPPATCH just sends a valid response, stating that
		// everything is fine, but doesn't do anything.

		// Retrieve the resources
		String tempLockOwner = "PROPATCH" + System.currentTimeMillis() + req.toString();

		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			StoredObject so = null;
			LockedObject lo = null;
			try
			{
				// check ACL

				so = this._store.getStoredObject(transaction, path);
				lo = this._resource_locks.getLockedObjectByPath(transaction, getCleanPath(path));

				if (so == null)
				{
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
					// we do not to continue since there is no root
					// resource
				}

				if (so.isNullResource())
				{
					String methodsAllowed = CalDAVMethods.determineMethodsAllowed(so);
					resp.addHeader("Allow", methodsAllowed);
					resp.sendError(CalDAVResponse.SC_METHOD_NOT_ALLOWED);
					return;
				}

				if (lo != null && lo.isExclusive())
				{
					// Object on specified path is LOCKED
					errorList = new Hashtable<String, Integer>();
					errorList.put(path, new Integer(CalDAVResponse.SC_LOCKED));
					sendReport(req, resp, errorList);
					return;
				}

				List<String> toset = null;
				List<String> toremove = null;
				List<String> tochange = new ArrayList<String>();
				// contains all properties from
				// toset and toremove

				path = getCleanPath(getRelativePath(req));

				Node tosetNode = null;
				Node toremoveNode = null;

				if (req.getContentLength() != 0)
				{
					DocumentBuilder documentBuilder = getDocumentBuilder();
					try
					{
						Document document = documentBuilder.parse(new InputSource(req.getInputStream()));
						tosetNode = XMLHelper.firstSubElement(
								XMLHelper.firstSubElement(document.getDocumentElement(), "set"), "prop");
						toremoveNode = XMLHelper.firstSubElement(
								XMLHelper.firstSubElement(document.getDocumentElement(), "remove"), "prop");
					}
					catch (Exception _ex)
					{
						logger.error(_ex);
						resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
						return;
					}
				}
				else
				{
					logger.error("Unable to get the requested content length.");
					resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
					return;
				}

				if (tosetNode != null)
				{
					toset = XMLHelper.getProperties(tosetNode);
					tochange.addAll(toset);
				}

				if (toremoveNode != null)
				{
					toremove = XMLHelper.getProperties(toremoveNode);
					tochange.addAll(toremove);
				}

				resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);
				resp.setContentType("text/xml; charset=UTF-8");

				try
				{
					XMLWriter XML = new XMLWriter();
					XML.setNameSpace("DAV:", "D");
					XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

					XML.addChildElement("D:multistatus");
					XML.addChildElement("D:response");
					String status = new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
							+ CalDAVResponse.getStatusText(CalDAVResponse.SC_OK));

					// Generating href element
					XML.addChildElement("D:href");

					String href = req.getContextPath();
					if ((href.endsWith("/")) && (path.startsWith("/")))
					{
						href += path.substring(1);
					}
					else
					{
						href += path;
					}
					if ((so.isFolder()) && (!href.endsWith("/")))
					{
						href += "/";
					}

					XML.setTextContent(rewriteUrl(href));
					XML.closeElement();

					for (String property : tochange)
					{
						XML.addChildElement("D:propstat");

						XML.addChildElement("D:prop");
						XML.addProperty(property);
						XML.closeElement();

						XML.addChildElement("D:status");
						XML.setTextContent(status);
						XML.closeElement();

						XML.closeElement();
					}

					XML.closeElement();

					XML.closeElement();

					XML.write(resp.getOutputStream());
				}
				catch (Exception _ex)
				{
					resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
				}
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
			catch (ServletException _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
				// File | Settings | File Templates.
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
}
