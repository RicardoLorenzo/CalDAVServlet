/**
 * 
 */
package com.whitebearsolutions.caldav.method;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.CalDAVServlet;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.VCalendar;
import com.whitebearsolutions.xml.VTimeZone;
import com.whitebearsolutions.xml.XMLHelper;

/**
 * @author Ricardo Lorenzo
 *
 */
public class MKCALENDAR extends CalDAVAbstractMethod
{
	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;
	private CalDAVResourceACL _resource_acl;
	private MKCOL _mkcol;

	public MKCALENDAR(CalDAVStore store, ResourceLocksMap resLocks, MKCOL _mkcol)
	{
		this._store = store;
		this._resource_locks = resLocks;
		this._mkcol = _mkcol;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getCleanPath(getRelativePath(req));
		String tempLockOwner = "MKCALENDAR" + System.currentTimeMillis() + req.toString();
		this._resource_acl = this._store.getResourceACL(transaction, path);

		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				this._resource_acl.getPrivilegeCollection()
						.checkPrincipalPrivilege(transaction.getPrincipal(), "write");

				if (req.getContentLength() > 0)
				{
					DocumentBuilder documentBuilder = getDocumentBuilder();
					try
					{
						Document document = documentBuilder.parse(new InputSource(req.getInputStream()));
						Element root = document.getDocumentElement();

						if ("mkcalendar".equals(root.getLocalName()))
						{
							VTimeZone _vtz = null;
							Node timezone = XMLHelper.findFirstSubElement(root, "calendar-timezone");
							if (timezone != null)
							{
								VCalendar _vc = CalDAVServlet.provider.createCalendar(timezone.getTextContent());
								_vtz = _vc.getTimeZone();
							}
							else
							{
								_vtz = new VTimeZone(TimeZone.getDefault().getDisplayName());
							}

							this._mkcol.mkcol(transaction, req, resp);

							path = path + "/calendar.ics";
							StringBuilder _sb = new StringBuilder();
							_sb.append("BEGIN:VCALENDAR\r\n");
							_sb.append(_vtz.toString());
							_sb.append("END:VCALENDAR\r\n");
							ByteArrayInputStream _bais = new ByteArrayInputStream(_sb.toString().getBytes());
							this._store.createResource(transaction, path);
							long length = this._store.setResourceContent(transaction, path, _bais, null, null);

							StoredObject so = this._store.getStoredObject(transaction, path);
							if (length != -1)
							{
								so.setResourceLength(length);
							}

							this._store.getResourceACL(transaction, path);
						}
						else
						{
							resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
						}
					}
					catch (Exception _ex)
					{
						logger.error(_ex);
						resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
						return;
					}
				}
				else
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
			Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
			errorList.put(path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
		}
	}
}
