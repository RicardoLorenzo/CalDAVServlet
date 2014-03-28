/**
 * 
 */
package com.whitebearsolutions.caldav.method;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.CalDAVServlet;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.xml.DateTime;
import com.whitebearsolutions.xml.Period;
import com.whitebearsolutions.xml.Person;
import com.whitebearsolutions.xml.URLEncoder;
import com.whitebearsolutions.xml.VAction;
import com.whitebearsolutions.xml.VCalendar;
import com.whitebearsolutions.xml.VCalendarCache;
import com.whitebearsolutions.xml.VEvent;
import com.whitebearsolutions.xml.VFreeBusy;
import com.whitebearsolutions.xml.VTimeZone;
import com.whitebearsolutions.xml.VTodo;
import com.whitebearsolutions.xml.XMLHelper;
import com.whitebearsolutions.xml.XMLWriter;

/**
 * @author Ricardo Lorenzo
 *
 */
public class REPORT extends CalDAVAbstractMethod
{
	protected static URLEncoder URL_ENCODER;
	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;
	private CalDAVResourceACL resource_acl;
	private boolean expand = false;

	public REPORT(CalDAVStore store, ResourceLocksMap resLocks)
	{
		this._store = store;
		this._resource_locks = resLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getCleanPath(getRelativePath(req));

		String tempLockOwner = "REPORT" + System.currentTimeMillis() + req.toString();
		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, 0, TEMP_TIMEOUT, TEMPORARY))
		{
			try
			{
				if (req.getContentLength() > 0)
				{
					resp.setStatus(CalDAVResponse.SC_MULTI_STATUS);
					resp.setContentType("application/xml; charset=UTF-8");

					XMLWriter XML = new XMLWriter();
					XML.setNameSpace("DAV:", "D");
					XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");
					XML.setNameSpace("http://calendarserver.org/ns/", "CS");

					XML.addChildElement("D:multistatus");

					DocumentBuilder documentBuilder = getDocumentBuilder();
					try
					{
						Document document = documentBuilder.parse(new InputSource(req.getInputStream()));
						Element rootElement = document.getDocumentElement();

						if ("calendar-query".equals(rootElement.getLocalName()))
						{
							Calendar start = null, end = null;
							Node filter = XMLHelper.findFirstSubElement(rootElement, "calendar-data");
							if (filter != null)
							{
								if (!XMLHelper.getChildElements(filter, "expand").isEmpty())
								{
									this.expand = true;
								}
							}
							filter = XMLHelper.findFirstSubElement(rootElement, "filter");
							if (filter != null)
							{
								for (Node n : XMLHelper.getAllChildElements(filter, "comp-filter"))
								{
									String type = ((Element) n).getAttribute("name");
									if (type == null
											|| (!type.toUpperCase().equals("VEVENT") && !type.toUpperCase().equals(
													"VTODO")))
									{
										continue;
									}
									if (n.hasChildNodes())
									{
										for (Node nn : XMLHelper.getChildElements(n))
										{
											Element e = (Element) nn;
											if ("time-range".equals(nn.getLocalName()))
											{
												Period p = null;
												if (e.hasAttribute("start") && e.hasAttribute("end"))
												{
													start = DateTime.getCalendarFromString(null,
															e.getAttribute("start"));
													end = DateTime.getCalendarFromString(null, e.getAttribute("end"));
													p = new Period(start, end);
												}
												processPeriodCalendarActions(transaction, path, p, XML, type);
											}
											else if ("prop-filter".equals(nn.getLocalName()))
											{
												String subtype = ((Element) nn).getAttribute("name");
												for (Node nnn : XMLHelper.getChildElements(nn))
												{
													if ("text-match".equals(nnn.getLocalName()))
													{
														processCalendarActions(transaction, path, nnn.getTextContent(),
																XML, type, subtype);
													}
												}
											}
										}
									}
									else
									{
										processCalendarActions(transaction, path, null, XML, type);
									}
								}
							}
						}
						else if ("calendar-multiget".equals(rootElement.getLocalName()))
						{
							for (Node calendar : XMLHelper.getChildElements(rootElement, "href"))
							{
								processGet(transaction, calendar.getTextContent(), XML);
							}
						}
					}
					catch (Exception _ex)
					{
						logger.error(_ex);
						resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
						return;
					}

					XML.closeElement();
					XML.write(resp.getOutputStream());
				}
			}
			catch (AccessDeniedException _ex)
			{
				resp.sendError(CalDAVResponse.SC_FORBIDDEN);
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
			Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
			errorList.put(path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
		}
	}

	private void processPeriodCalendarActions(CalDAVTransaction transaction, String path, Period p, XMLWriter XML,
			String type) throws Exception
	{
		if ("VFREEBUSY".equals(type))
		{
			String[] names = this._store.getChildrenNames(transaction, path);
			if (names != null)
			{
				for (String name : names)
				{
					processCalendarActions(transaction, path + (path.endsWith("/") ? "" : "/") + name, p, XML, type);
				}
			}
			else
			{
				processCalendarActions(transaction, path, p, XML, type);
			}
		}
		else
		{
			processCalendarActions(transaction, path, p, XML, type);
		}
	}

	private void processCalendarActions(CalDAVTransaction transaction, String path, Period p, XMLWriter XML, String type)
			throws Exception
	{
		String href = path + "/calendar.ics";
		File _f = new File(this._store.getRootPath() + href);
		if (_f.exists())
		{
			String eTag = getETag(this._store.getStoredObject(transaction, href));
			String cTag = getCTag(this._store.getStoredObject(transaction, href));
			if (!"VFREEBUSY".equals(type))
			{
				try
				{
					this.resource_acl = this._store.getResourceACL(transaction, href);
					this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(),
							"read");
				}
				catch (AccessDeniedException _ex)
				{
					return;
				}
			}

			VCalendar vc = VCalendarCache.getVCalendar(_f);
			if ("VEVENT".equals(type))
			{
				if (p == null)
				{
					for (VEvent ve : vc.getVevents())
					{
						if (ve == null)
						{
							continue;
						}
						printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
					}
				}
				else
				{
					if (this.expand)
					{
						for (VEvent ve : vc.getRecurrenceVevents(p))
						{
							if (ve == null)
							{
								continue;
							}
							printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
						}
					}
					else
					{
						for (VEvent ve : vc.getVevents(p))
						{
							if (ve == null)
							{
								continue;
							}
							printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
						}
					}
				}
			}
			else if ("VTODO".equals(type))
			{
				if (p == null)
				{
					for (VTodo vt : vc.getVtodos())
					{
						if (vt == null)
						{
							continue;
						}
						printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
					}
				}
				else
				{
					if (this.expand)
					{
						for (VTodo vt : vc.getRecurrenceVtodos(p))
						{
							if (vt == null)
							{
								continue;
							}
							printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
						}
					}
					else
					{
						for (VTodo vt : vc.getVtodos(p))
						{
							if (vt == null)
							{
								continue;
							}
							printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
						}
					}
				}
			}
			else if ("VFREEBUSY".equals(type))
			{
				if (p != null)
				{
					VFreeBusy _vfb = vc.getFreeBusy(p);
					_vfb.setOrganizer("", new Person("CN=" + transaction.getPrincipal().getName(), Person.ORGANIZER));
					printVFreeBusy(XML, path, eTag, vc.getTimeZone(), _vfb);
				}
			}
		}
	}

	private void processCalendarActions(CalDAVTransaction transaction, String path, String match, XMLWriter XML,
			String type, String subtype)
	{
		String href = path + "/calendar.ics";
		try
		{
			this.resource_acl = this._store.getResourceACL(transaction, href);
			this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(), "read");
		}
		catch (AccessDeniedException _ex)
		{
			return;
		}

		File _f = new File(this._store.getRootPath() + href);
		if (_f.exists())
		{
			String eTag = getETag(this._store.getStoredObject(transaction, href));
			String cTag = getCTag(this._store.getStoredObject(transaction, href));
			VCalendar vc = VCalendarCache.getVCalendar(_f);
			if ("VEVENT".equals(type))
			{
				if ("UID".equals(subtype))
				{
					VEvent ve = vc.getVevent(match);
					if (ve != null)
					{
						printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
					}
				}
			}
			else if ("VTODO".equals(type))
			{
				if ("UID".equals(subtype))
				{
					VTodo vt = vc.getVtodo(match);
					if (vt != null)
					{
						printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
					}
				}
			}
		}
	}

	private void processGet(CalDAVTransaction transaction, String path, XMLWriter XML) throws IOException
	{
		String href = path.substring(0, path.lastIndexOf("/"));
		while (href.endsWith(".ics"))
		{
			href = href.substring(0, href.lastIndexOf("/"));
		}
		if (href.startsWith("/caldav"))
		{
			href = href.substring(7);
		}
		href = href.concat("/calendar.ics");
		try
		{
			this.resource_acl = this._store.getResourceACL(transaction, href);
			this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(transaction.getPrincipal(), "read");
		}
		catch (AccessDeniedException _ex)
		{
			return;
		}

		File _f = new File(this._store.getRootPath() + href);
		if (_f.exists())
		{
			String eTag = getETag(this._store.getStoredObject(transaction, href));
			String cTag = getCTag(this._store.getStoredObject(transaction, href));
			String uid = path.substring(path.lastIndexOf("/") + 1);
			if (uid.endsWith(".ics"))
			{
				uid = uid.substring(0, uid.length() - 4);
			}

			VCalendar vc = VCalendarCache.getVCalendar(_f);
			try
			{
				VEvent ve = vc.getVevent(uid);
				if (ve != null)
				{
					printVAction(XML, path, eTag, cTag, vc.getTimeZone(), ve);
				}
			}
			catch (Exception _ex)
			{
				VTodo vt = vc.getVtodo(uid);
				if (vt != null)
				{
					printVAction(XML, path, eTag, cTag, vc.getTimeZone(), vt);
				}
			}
		}
	}

	private static void printVAction(XMLWriter XML, String path, String eTag, String cTag, VTimeZone tz, VAction va)
	{
		if (!path.endsWith("/"))
		{
			path = path.concat("/");
		}

		XML.addChildElement("D:response");

		XML.addChildElement("D:href");
		XML.setTextContent(path + va.getUid() + ".ics");
		XML.closeElement();

		XML.addChildElement("D:propstat");
		XML.addChildElement("D:prop");

		XML.addChildElement("D:getetag");
		XML.setTextContent(eTag);
		XML.closeElement();

		XML.addChildElement("CS:getctag");
		XML.setTextContent(cTag);
		XML.closeElement();

		XML.addChildElement("C:calendar-data");

		StringBuilder _sb = new StringBuilder();
		_sb.append("BEGIN:VCALENDAR\n");
		_sb.append("VERSION:" + CalDAVServlet.provider.getVersion() + "\n");
		_sb.append("PRODID:" + CalDAVServlet.provider.getProdId() + "\n");
		if (tz != null)
		{
			_sb.append(tz.toString());
		}
		if (va instanceof VEvent)
		{
			_sb.append(((VEvent) va).toString(tz));
		}
		else if (va instanceof VTodo)
		{
			_sb.append(((VTodo) va).toString(tz));
		}
		else
		{
			_sb.append(va.toString());
		}
		_sb.append("END:VCALENDAR\n");

		XML.setDataContent(_sb.toString());

		XML.closeElement();

		XML.addChildElement("D:status");
		XML.setTextContent(new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
				+ CalDAVResponse.getStatusText(CalDAVResponse.SC_OK)));
		XML.closeElement();

		XML.closeElement();

		XML.closeElement();

		XML.closeElement();
	}

	private static void printVFreeBusy(XMLWriter XML, String path, String eTag, VTimeZone tz, VFreeBusy vfb)
	{
		if (!path.endsWith("/"))
		{
			path = path.concat("/");
		}

		XML.addChildElement("D:response");

		XML.addChildElement("D:href");
		XML.setTextContent(path + "calendar.ics");
		XML.closeElement();

		XML.addChildElement("D:propstat");
		XML.addChildElement("D:prop");

		XML.addChildElement("D:getetag");
		XML.setTextContent(eTag);
		XML.closeElement();

		XML.addChildElement("C:calendar-data");

		StringBuilder _sb = new StringBuilder();
		_sb.append("BEGIN:VCALENDAR\n");
		_sb.append("VERSION:" + CalDAVServlet.provider.getVersion() + "\n");
		_sb.append("PRODID:" + CalDAVServlet.provider.getProdId() + "\n");
		if (tz != null)
		{
			_sb.append(tz.toString());
		}
		_sb.append(vfb.toString());
		_sb.append("END:VCALENDAR\n");

		XML.setDataContent(_sb.toString());

		XML.closeElement();

		XML.addChildElement("D:status");
		XML.setTextContent(new String("HTTP/1.1 " + CalDAVResponse.SC_OK + " "
				+ CalDAVResponse.getStatusText(CalDAVResponse.SC_OK)));
		XML.closeElement();

		XML.closeElement();

		XML.closeElement();

		XML.closeElement();
	}
}
