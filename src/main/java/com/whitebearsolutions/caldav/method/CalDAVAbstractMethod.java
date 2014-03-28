package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.LockedObject;
import com.whitebearsolutions.caldav.locking.ResourceLocks;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.URLEncoder;
import com.whitebearsolutions.xml.VAction;
import com.whitebearsolutions.xml.XMLWriter;

public abstract class CalDAVAbstractMethod implements com.whitebearsolutions.caldav.CalDAVMethod
{
	Logger logger = LogManager.getLogger();
	protected static URLEncoder URL_ENCODER;
	protected static final int INFINITY = 3;
	protected static final SimpleDateFormat CREATION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	protected static final SimpleDateFormat LAST_MODIFIED_DATE_FORMAT = new SimpleDateFormat(
			"EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

	static
	{
		CREATION_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
		LAST_MODIFIED_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
		URL_ENCODER = new URLEncoder();
		URL_ENCODER.addSafeCharacter('-');
		URL_ENCODER.addSafeCharacter('_');
		URL_ENCODER.addSafeCharacter('.');
		URL_ENCODER.addSafeCharacter('*');
		URL_ENCODER.addSafeCharacter('/');
	}

	protected static int BUF_SIZE = 65536;
	protected static final int DEFAULT_TIMEOUT = 3600;
	protected static final int MAX_TIMEOUT = 604800;
	protected static final boolean TEMPORARY = true;
	protected static final int TEMP_TIMEOUT = 10;

	/**
	 * Devuelve el "path" relativo asociado al servlet.
	 * 
	 * @param request
	 *            El servlet request que se debe procesar.
	 */
	protected String getRelativePath(HttpServletRequest request)
	{
		if (request.getAttribute("javax.servlet.include.request_uri") != null)
		{
			String result = String.valueOf(request.getAttribute("javax.servlet.include.path_info"));
			if ((result == null) || (result.equals("")))
				result = "/";
			return result;
		}

		String result = request.getPathInfo();
		if ((result == null) || (result.equals("")))
		{
			result = "/";
		}
		return result;
	}

	/**
	 * creates the parent path from the given path by removing the last '/' and
	 * everything after that
	 * 
	 * @param path
	 *            the path
	 * @return parent path
	 */
	protected String getParentPath(String path)
	{
		int slash = path.lastIndexOf('/');
		if (slash != -1)
		{
			return path.substring(0, slash);
		}
		return null;
	}

	/**
	 * removes a / at the end of the path string, if present
	 * 
	 * @param path
	 *            the path
	 * @return the path without trailing /
	 */
	protected String getCleanPath(String path)
	{
		if (path.endsWith("/") && path.length() > 1)
			path = path.substring(0, path.length() - 1);
		return path;
	}

	/**
	 * Return JAXP document builder instance.
	 */
	protected DocumentBuilder getDocumentBuilder() throws ServletException
	{
		DocumentBuilder documentBuilder = null;
		DocumentBuilderFactory documentBuilderFactory = null;
		try
		{
			documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(true);
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
		}
		catch (ParserConfigurationException e)
		{
			logger.error(e);
			throw new ServletException("jaxp failed");
		}
		return documentBuilder;
	}

	/**
	 * reads the depth header from the request and returns it as a int
	 * 
	 * @param req
	 * @return the depth from the depth header
	 */
	protected int getDepth(HttpServletRequest req)
	{
		int depth = INFINITY;
		String depthStr = req.getHeader("Depth");
		if (depthStr != null)
		{
			if (depthStr.equals("0"))
			{
				depth = 0;
			}
			else if (depthStr.equals("1"))
			{
				depth = 1;
			}
		}
		return depth;
	}

	/**
	 * URL rewriter.
	 * 
	 * @param path
	 *            Path which has to be rewiten
	 * @return the rewritten path
	 */
	protected String rewriteUrl(String path)
	{
		return URL_ENCODER.encode(path);
	}

	/**
	 * Get the ETag associated with a file.
	 * 
	 * @param StoredObject
	 *            StoredObject to get resourceLength, lastModified and a
	 *            hashCode of StoredObject
	 * @return the ETag
	 */
	protected String getETag(StoredObject so)
	{
		StringBuilder _sb = new StringBuilder();
		_sb.append("\"");
		if (so != null && so.isResource())
		{
			_sb.append(so.getResourceLength());
			_sb.append("-");
			_sb.append(so.getLastModified().getTime());
		}
		else
		{
			_sb.append("0000-0000000000000000");
		}
		_sb.append("\"");
		return _sb.toString();
	}

	/**
	 * Get the ETag associated with a file.
	 * 
	 * @param StoredObject
	 *            StoredObject to get resourceLength, lastModified and a
	 *            hashCode of StoredObject
	 * @return the ETag
	 */
	protected String getETag(VAction va)
	{
		StringBuilder _sb = new StringBuilder();
		_sb.append("\"");
		if (va != null && va.hasLastModified())
		{
			_sb.append(va.toString().length());
			_sb.append("-");
			_sb.append(va.getLastModified().getTimeInMillis());
		}
		else
		{
			_sb.append("0000-0000000000000000");
		}
		_sb.append("\"");
		return _sb.toString();
	}

	/**
	 * Get the CTag associated with a file.
	 * 
	 * @param StoredObject
	 *            StoredObject to get resourceLength, lastModified and a
	 *            hashCode of StoredObject
	 * @return the CTag
	 */
	protected String getCTag(StoredObject so)
	{
		StringBuilder _sb = new StringBuilder();
		if (so != null && so.isResource())
		{
			_sb.append(so.getResourceLength());
			_sb.append("-");
			_sb.append(so.getLastModified().getTime());
		}
		else
		{
			_sb.append("0000-0000000000000000");
		}
		return _sb.toString();
	}

	protected String[] getLockIdFromIfHeader(HttpServletRequest req)
	{
		String[] ids = new String[2];
		String id = req.getHeader("If");

		if (id != null && !id.equals(""))
		{
			if (id.indexOf(">)") == id.lastIndexOf(">)"))
			{
				id = id.substring(id.indexOf("(<"), id.indexOf(">)"));

				if (id.indexOf("locktoken:") != -1)
				{
					id = id.substring(id.indexOf(':') + 1);
				}
				ids[0] = id;
			}
			else
			{
				String firstId = id.substring(id.indexOf("(<"), id.indexOf(">)"));
				if (firstId.indexOf("locktoken:") != -1)
				{
					firstId = firstId.substring(firstId.indexOf(':') + 1);
				}
				ids[0] = firstId;

				String secondId = id.substring(id.lastIndexOf("(<"), id.lastIndexOf(">)"));
				if (secondId.indexOf("locktoken:") != -1)
				{
					secondId = secondId.substring(secondId.indexOf(':') + 1);
				}
				ids[1] = secondId;
			}

		}
		else
		{
			ids = null;
		}
		return ids;
	}

	protected String getLockIdFromLockTokenHeader(HttpServletRequest req)
	{
		String id = req.getHeader("Lock-Token");

		if (id != null)
		{
			id = id.substring(id.indexOf(":") + 1, id.indexOf(">"));

		}

		return id;
	}

	/**
	 * Checks if locks on resources at the given path exists and if so checks
	 * the If-Header to make sure the If-Header corresponds to the locked
	 * resource. Returning true if no lock exists or the If-Header is
	 * corresponding to the locked resource
	 * 
	 * @param req
	 *            Servlet request
	 * @param resp
	 *            Servlet response
	 * @param resourceLocks
	 * @param path
	 *            path to the resource
	 * @param errorList
	 *            List of error to be displayed
	 * @return true if no lock on a resource with the given path exists or if
	 *         the If-Header corresponds to the locked resource
	 * @throws IOException
	 * @throws LockFailedException
	 */
	protected boolean checkLocks(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp,
			ResourceLocks resourceLocks, String path) throws IOException, LockException
	{

		LockedObject loByPath = resourceLocks.getLockedObjectByPath(transaction, path);
		if (loByPath != null)
		{

			if (loByPath.isShared())
				return true;

			// the resource is locked
			String[] lockTokens = getLockIdFromIfHeader(req);
			String lockToken = null;
			if (lockTokens != null)
				lockToken = lockTokens[0];
			else
			{
				return false;
			}
			if (lockToken != null)
			{
				LockedObject loByIf = resourceLocks.getLockedObjectByID(transaction, lockToken);
				if (loByIf == null)
				{
					// no locked resource to the given lockToken
					return false;
				}
				if (!loByIf.equals(loByPath))
				{
					loByIf = null;
					return false;
				}
				loByIf = null;
			}

		}
		loByPath = null;
		return true;
	}

	/**
	 * Send a multistatus element containing a complete error report to the
	 * client.
	 * 
	 * @param req
	 *            Servlet request
	 * @param resp
	 *            Servlet response
	 * @param errorList
	 *            List of error to be displayed
	 */
	protected void sendReport(HttpServletRequest req, HttpServletResponse resp, Hashtable<String, Integer> errorList)
			throws IOException
	{

		resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);

		String absoluteUri = req.getRequestURI();

		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:multistatus");

			Enumeration<String> pathList = errorList.keys();
			while (pathList.hasMoreElements())
			{
				String errorPath = (String) pathList.nextElement();
				int errorCode = ((Integer) errorList.get(errorPath)).intValue();

				XML.addChildElement("D:response");

				XML.addChildElement("D:href");
				String toAppend = null;
				if (absoluteUri.endsWith(errorPath))
				{
					toAppend = absoluteUri;

				}
				else if (absoluteUri.contains(errorPath))
				{
					int endIndex = absoluteUri.indexOf(errorPath) + errorPath.length();
					toAppend = absoluteUri.substring(0, endIndex);
				}
				if (!toAppend.startsWith("/") && !toAppend.startsWith("http:"))
				{
					toAppend = "/" + toAppend;
				}
				XML.setTextContent(errorPath);
				XML.closeElement();
				XML.addChildElement("D:status");
				XML.setTextContent("HTTP/1.1 " + errorCode + " " + CalDAVResponse.getStatusText(errorCode));
				XML.closeElement();

				XML.closeElement();
			}

			XML.closeElement();

			Writer writer = resp.getWriter();
			writer.write(XML.toString());
			writer.close();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
		}
	}

	protected void sendPrivilegeError(HttpServletResponse response, String uri, String privilege) throws IOException
	{
		response.setStatus(CalDAVResponse.SC_FORBIDDEN);

		HashMap<String, String> namespaces = new HashMap<String, String>();
		namespaces.put("DAV:", "D");
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:error");
			XML.addChildElement("D:need-privleges");

			XML.addChildElement("D:href");
			XML.setTextContent(uri);
			XML.closeElement();

			XML.addChildElement("D:privilege");
			XML.setTextContent(privilege);
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			Writer writer = response.getWriter();
			writer.write(XML.toString());
			writer.close();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
		}
	}
}
