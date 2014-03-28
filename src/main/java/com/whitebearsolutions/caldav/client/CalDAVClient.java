package com.whitebearsolutions.caldav.client;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.whitebearsolutions.caldav.CalDAVServlet;
import com.whitebearsolutions.caldav.CalendarProvider;
import com.whitebearsolutions.xml.DateTime;
import com.whitebearsolutions.xml.HTTPConnection;
import com.whitebearsolutions.xml.Period;
import com.whitebearsolutions.xml.VCalendar;
import com.whitebearsolutions.xml.VEvent;
import com.whitebearsolutions.xml.VFreeBusy;
import com.whitebearsolutions.xml.VTimeZone;
import com.whitebearsolutions.xml.VTodo;
import com.whitebearsolutions.xml.XMLHelper;
import com.whitebearsolutions.xml.XMLWriter;

public class CalDAVClient
{
	Logger logger = LogManager.getLogger();
	private static final String CRLF = "\r\n";
	public static final String ALL = "all";
	public static final String READ = "read";
	public static final String WRITE = "write";
	private HTTPConnection _conn;
	private CalendarProvider provider;

	public CalDAVClient(String hostName)
	{
		this._conn = new HTTPConnection(hostName);
		this._conn.setUserAgent("WBSGo/WBSVia caldav client");
	}

	public void setPort(int port)
	{
		this._conn.setPort(port);
	}

	public void setUser(String user, String password)
	{
		this._conn.setUser(user, password);
	}

	public void mkCollection(String path) throws CalDAVClientException
	{
		try
		{
			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.connect(HTTPConnection.MKCOL);
			if (this._conn.getResponseCode() != 201)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public void grantACL(String path, String user, String permission) throws CalDAVClientException
	{
		if (!permission.equals(READ) && !permission.equals(WRITE))
		{
			throw new CalDAVClientException("invalid permission");
		}
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:acl");
			XML.addChildElement("D:ace");

			XML.addChildElement("D:principal");
			XML.addChildElement("D:href");
			XML.setTextContent("http://" + this._conn.getServer() + "/users/" + user);
			XML.closeElement();
			XML.closeElement();

			XML.addChildElement("D:grant");
			XML.addChildElement("D:privilege");
			XML.addChildElement("D:" + permission);
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.ACL);
			if (this._conn.getResponseCode() != 200)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public void denyACL(String path, String user, String permission) throws CalDAVClientException
	{
		if (!permission.equals(READ) && !permission.equals(WRITE))
		{
			throw new CalDAVClientException("invalid permission");
		}
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:acl");
			XML.addChildElement("D:ace");

			XML.addChildElement("D:principal");
			XML.addChildElement("D:href");
			XML.setTextContent("http://" + this._conn.getServer() + "/users/" + user);
			XML.closeElement();
			XML.closeElement();

			XML.addChildElement("D:deny");
			XML.addChildElement("D:privilege");
			XML.addChildElement("D:" + permission);
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.ACL);
			if (this._conn.getResponseCode() != 200)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<String> getCalendars(String path) throws CalDAVClientException
	{
		List<String> calendars = new ArrayList<String>();
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:propfind");
			XML.addChildElement("D:prop");

			XML.addProperty("D:creationdate");
			XML.addProperty("D:getetag");
			XML.addProperty("D:supportedlock");
			XML.addProperty("D:lockdiscovery");
			XML.addProperty("D:resourcetype");

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.setHeader("Depth", "1");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.PROPFIND);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			Element root = document.getDocumentElement();

			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n1 = XMLHelper.findFirstSubElement(response, "href");
					Node n2 = XMLHelper.findFirstSubElement(response, "prop");
					if (n1 != null && n2 != null)
					{
						Map<String, String> properties = getPropertiesForNode(n2, 1);
						if (properties.containsKey("resourcetype"))
						{
							if (properties.get("resourcetype").contains("calendar"))
							{
								calendars.add(n1.getTextContent());
							}
						}
					}
				}
			}
			else
			{
				throw new Exception("invalid WebDAV resource");
			}
			return calendars;
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<String> getCalendars(String path, String permission) throws CalDAVClientException
	{
		List<String> calendars = new ArrayList<String>();
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:propfind");
			XML.addChildElement("D:prop");

			XML.addProperty("D:creationdate");
			XML.addProperty("D:getetag");
			XML.addProperty("D:current-user-privilege-set");
			XML.addProperty("D:supportedlock");
			XML.addProperty("D:lockdiscovery");
			XML.addProperty("D:resourcetype");

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.setHeader("Depth", "1");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.PROPFIND);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			Element root = document.getDocumentElement();

			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n1 = XMLHelper.findFirstSubElement(response, "href");
					Node n2 = XMLHelper.findFirstSubElement(response, "prop");
					if (n1 != null && n2 != null)
					{
						Map<String, String> properties = getPropertiesForNode(n2, 1);
						if (properties.containsKey("resourcetype"))
						{
							if (properties.get("resourcetype").contains("calendar")
									&& properties.get("current-user-privilege-set/privilege") != null
									&& (properties.get("current-user-privilege-set/privilege").contains(permission) || properties
											.get("current-user-privilege-set/privilege").contains("all")))
							{
								calendars.add(n1.getTextContent());
							}
						}
					}
				}
			}
			else
			{
				throw new Exception("invalid WebDAV resource");
			}
			return calendars;
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public void mkCalendar(String path) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");
			VTimeZone _vtz = new VTimeZone(null);

			XML.addChildElement("C:mkcalendar");
			XML.addChildElement("D:set");
			XML.addChildElement("D:prop");

			XML.addChildElement("D:displayname");
			XML.setTextContent("WBSGo calendar");
			XML.closeElement();

			XML.addChildElement("C:calendar-timezone");
			XML.setDataContent(_vtz.toString());
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("application/xml; charset=utf-8");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.MKCALENDAR);
			if (this._conn.getResponseCode() != 201)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public void delete(String path) throws CalDAVClientException
	{
		try
		{
			this._conn.setPath(path);
			this._conn.connect(HTTPConnection.DELETE);
			if (this._conn.getResponseCode() != 204)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<String> getUserACL(String path, String user) throws CalDAVClientException
	{
		List<String> privileges = new ArrayList<String>();
		if (!user.contains("/"))
		{
			user = "http://" + this._conn.getServer() + "/acl/users/" + user;
		}
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("D:propfind");
			XML.addChildElement("D:prop");

			XML.addProperty("D:acl");

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.setHeader("Depth", "0");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.PROPFIND);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			if (!path.endsWith("/"))
			{
				path = path.concat("/");
			}
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "href");
					if (!path.equals(n.getTextContent())
							&& !path.substring(0, path.length() - 1).equals(n.getTextContent()))
					{
						continue;
					}
					n = XMLHelper.findFirstSubElement(response, "acl");
					for (Node nn : XMLHelper.getChildElements(n, "ace"))
					{
						Node nnn = XMLHelper.findFirstSubElement(nn, "principal");
						if (nnn == null)
						{
							continue;
						}
						if (!user.equals(nnn.getTextContent()))
						{
							continue;
						}

						nnn = XMLHelper.findFirstSubElement(nn, "grant");
						if (nnn != null)
						{
							for (Node p : XMLHelper.getChildElements(nnn, "privilege"))
							{
								if (p.hasChildNodes())
								{
									privileges.add(p.getFirstChild().getLocalName());
								}
							}
						}

						/*
						 * nnn = XMLHelper.findFirstSubElement(nn, "deny");
						 * for(Node p : XMLHelper.getChildElements(nn,
						 * "privilege")) { if(p.hasChildNodes()) {
						 * privileges.add(p.getFirstChild().getLocalName()); } }
						 */
					}
					break;
				}
			}
			else
			{
				throw new Exception("invalid WebDAV resource");
			}
			return privileges;
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public Map<String, String> propfind(String path) throws CalDAVClientException
	{
		Map<String, String> properties = new HashMap<String, String>();
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");
			XML.setNameSpace("http://calendarserver.org/ns/", "CS");

			XML.addChildElement("D:propfind");
			XML.addChildElement("D:prop");

			XML.addProperty("D:creationdate");
			XML.addProperty("D:getcontentlength");
			XML.addProperty("D:displayname");
			XML.addProperty("D:source");
			XML.addProperty("D:current-user-privilege-set");
			XML.addProperty("D:getetag");
			XML.addProperty("CS:getctag");
			XML.addProperty("D:supportedlock");
			XML.addProperty("D:lockdiscovery");
			XML.addProperty("D:resourcetype");

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContentType("text/xml; charset=utf-8");
			this._conn.setHeader("Depth", "0");
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.PROPFIND);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			if (!path.endsWith("/"))
			{
				path = path.concat("/");
			}
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "href");
					String rpath = n.getTextContent();
					if (!rpath.endsWith("/"))
					{
						rpath = rpath.concat("/");
					}
					if (!path.equals(rpath))
					{
						continue;
					}
					n = XMLHelper.findFirstSubElement(response, "prop");
					properties.putAll(getPropertiesForNode(n, 1));
					break;
				}

				if (properties.containsKey("resourcetype"))
				{
					if (!properties.get("resourcetype").contains("calendar"))
					{
						throw new Exception("resource is valid WebDAV collection but is not a valid CalDAV collection");
					}
				}
				else
				{
					throw new Exception("unknown WebDAV resource");
				}
			}
			else
			{
				throw new Exception("invalid WebDAV resource");
			}
			return properties;
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public void put(String path, VEvent ve) throws CalDAVClientException
	{
		try
		{
			StringBuilder _sb = new StringBuilder();
			_sb.append("BEGIN:VCALENDAR");
			_sb.append(CRLF);
			_sb.append("VERSION:");
			_sb.append(provider.getVersion());
			_sb.append(CRLF);
			_sb.append("PRODID:");
			_sb.append(provider.getProdId());
			_sb.append(CRLF);
			_sb.append(ve.toString());
			_sb.append("END:VCALENDAR");
			_sb.append(CRLF);

			this._conn.setPath(path);
			this._conn.setContent(_sb.toString());
			this._conn.connect(HTTPConnection.PUT);
			if (this._conn.getResponseCode() != 201)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public void put(String path, VTodo vt) throws CalDAVClientException
	{
		try
		{
			StringBuilder _sb = new StringBuilder();
			_sb.append("BEGIN:VCALENDAR");
			_sb.append(CRLF);
			_sb.append("VERSION:");
			_sb.append(provider.getVersion());
			_sb.append(CRLF);
			_sb.append("PRODID:");
			_sb.append(provider.getProdId());
			_sb.append(CRLF);
			_sb.append(vt.toString());
			_sb.append("END:VCALENDAR");
			_sb.append(CRLF);

			this._conn.setPath(path);
			this._conn.setContent(_sb.toString());
			this._conn.connect(HTTPConnection.PUT);
			if (this._conn.getResponseCode() != 201)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public VEvent reportVEventByUid(String path, String uid) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VEVENT");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VEVENT");

			XML.addChildElement("C:prop-filter");
			XML.addAttribute("name", "UID");
			XML.addChildElement("C:text-match");
			XML.setTextContent(uid);
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			VCalendar _vc = CalDAVServlet.provider.createCalendar();
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						for (VEvent ve : _tvc.getVevents())
						{
							_vc.addVevent(ve);
						}
					}
				}
			}
			if (_vc.getVevents().isEmpty())
			{
				return null;
			}
			return _vc.getVevents().get(0);
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<VEvent> reportVEvents(String path) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VEVENT");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VEVENT");
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			VCalendar _vc = provider.createCalendar();
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						for (VEvent ve : _tvc.getVevents())
						{
							_vc.addVevent(ve);
						}
					}
				}
			}

			return _vc.getVevents();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<VEvent> reportVEventDay(String path, Calendar date) throws CalDAVClientException
	{
		Calendar start = (Calendar) date.clone();
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.MILLISECOND, 0);
		start.set(Calendar.HOUR_OF_DAY, 0);
		Calendar end = (Calendar) start.clone();
		end.add(Calendar.DAY_OF_MONTH, 1);

		Period p = new Period(start, end);
		return reportVEvents(path, p);
	}

	public Map<Integer, List<VEvent>> reportVEventDayMap(String path, Calendar date) throws CalDAVClientException
	{
		Map<Integer, List<VEvent>> eventsMap = new HashMap<Integer, List<VEvent>>();
		Calendar start = (Calendar) date.clone();
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.HOUR_OF_DAY, 0);
		Calendar end = (Calendar) start.clone();
		end.add(Calendar.DAY_OF_MONTH, 1);

		Period p = new Period(start, end);
		for (VEvent ve : reportVEvents(path, p))
		{
			Calendar c = ve.getDTStart();
			List<VEvent> events = new ArrayList<VEvent>();
			if (eventsMap.containsKey(c.get(Calendar.HOUR_OF_DAY)))
			{
				events.addAll(eventsMap.get(c.get(Calendar.HOUR_OF_DAY)));
			}
			events.add(ve);
			eventsMap.put(c.get(Calendar.HOUR_OF_DAY), events);
		}
		return eventsMap;
	}

	public Map<Integer, List<VEvent>> reportVEventWeekMap(String path, Calendar date) throws CalDAVClientException
	{
		Map<Integer, List<VEvent>> week_events = new HashMap<Integer, List<VEvent>>();
		Calendar _start = (Calendar) date.clone();
		_start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		_start.set(Calendar.HOUR_OF_DAY, 0);
		_start.set(Calendar.MINUTE, 0);
		_start.set(Calendar.SECOND, 0);
		_start.set(Calendar.MILLISECOND, 0);
		Calendar _end = (Calendar) _start.clone();
		_end.add(Calendar.WEEK_OF_MONTH, 1);

		for (VEvent _ve : reportVEvents(path, new Period(_start, _end)))
		{
			List<Period> _periods = _ve.getPeriods(new Period(_start, _end));
			if (_periods != null && !_periods.isEmpty())
			{
				for (Period p : _periods)
				{
					Calendar offset = (Calendar) p.getStart().clone();
					for (; offset.before(p.getEnd()) && offset.before(_end); offset.add(Calendar.DAY_OF_WEEK, 1))
					{
						Map<String, VEvent> vevents = new HashMap<String, VEvent>();
						if (week_events.containsKey(offset.get(Calendar.DAY_OF_WEEK)))
						{
							for (VEvent _tve : week_events.get(offset.get(Calendar.DAY_OF_WEEK)))
							{
								vevents.put(_tve.getUid(), _tve);
							}
						}
						if (!vevents.containsKey(_ve.getUid()))
						{
							vevents.put(_ve.getUid(), _ve);
						}
						week_events.put(offset.get(Calendar.DAY_OF_WEEK), new ArrayList<VEvent>(vevents.values()));
					}
				}
			}
		}
		return week_events;
	}

	public Map<Integer, List<VEvent>> reportVEventMonthMap(String path, Calendar date) throws CalDAVClientException
	{
		Map<Integer, List<VEvent>> eventsMap = new HashMap<Integer, List<VEvent>>();
		Calendar start = (Calendar) date.clone();
		start.set(Calendar.MILLISECOND, 0);
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.HOUR, 0);
		start.set(Calendar.DAY_OF_MONTH, 1);
		Calendar end = (Calendar) start.clone();
		end.add(Calendar.MONTH, 1);

		Period periodo = new Period(start, end);
		for (VEvent ve : reportVEvents(path, periodo))
		{
			List<Period> _periods = ve.getPeriods(periodo);
			if (_periods != null && !_periods.isEmpty())
			{
				for (Period p : _periods)
				{
					Calendar offset = (Calendar) p.getStart().clone();
					for (; offset.before(p.getEnd()) && offset.before(end); offset.add(Calendar.DAY_OF_MONTH, 1))
					{
						ArrayList<VEvent> vevents = new ArrayList<VEvent>();
						if (eventsMap.containsKey(offset.get(Calendar.DAY_OF_MONTH)))
						{
							vevents.addAll(eventsMap.get(offset.get(Calendar.DAY_OF_MONTH)));
						}
						vevents.add(ve);
						eventsMap.put(offset.get(Calendar.DAY_OF_MONTH), vevents);
					}
				}
			}
		}
		return eventsMap;
	}

	public List<VEvent> reportVEvents(String path, Period p) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VEVENT");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VEVENT");
			XML.addChildElement("C:time-range");
			XML.addAttribute("start", DateTime.getUTCTime(p.getStart()));
			XML.addAttribute("end", DateTime.getUTCTime(p.getEnd()));
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			VCalendar _vc = provider.createCalendar();
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						for (VEvent ve : _tvc.getVevents())
						{
							_vc.addVevent(ve);
						}
					}
				}
			}

			return _vc.getVevents();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public VTodo reportVTodoByUid(String path, String uid) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VTODO");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VTODO");

			XML.addChildElement("C:prop-filter");
			XML.addAttribute("name", "UID");
			XML.addChildElement("C:text-match");
			XML.setTextContent(uid);
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			VCalendar _vc = provider.createCalendar();
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						for (VTodo vt : _tvc.getVtodos())
						{
							_vc.addVtodo(vt);
						}
					}
				}
			}
			if (_vc.getVtodos().isEmpty())
			{
				return null;
			}
			return _vc.getVtodos().get(0);
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<VTodo> reportVTodos(String path) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VTODO");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();

			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VTODO");
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			VCalendar _vc = provider.createCalendar();
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						for (VTodo vt : _tvc.getVtodos())
						{
							_vc.addVtodo(vt);
						}
					}
				}
			}

			return _vc.getVtodos();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<VTodo> reportVTodos(String path, Period p) throws CalDAVClientException
	{
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VTODO");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VTODO");
			XML.addChildElement("C:time-range");
			XML.addAttribute("start", DateTime.getUTCTime(p.getStart()));
			XML.addAttribute("end", DateTime.getUTCTime(p.getEnd()));
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			VCalendar _vc = provider.createCalendar();
			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						for (VTodo vt : _tvc.getVtodos())
						{
							_vc.addVtodo(vt);
						}
					}
				}
			}

			return _vc.getVtodos();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	public List<VFreeBusy> reportVFreeBusy(String path, Period p) throws CalDAVClientException
	{
		List<VFreeBusy> busy = new ArrayList<VFreeBusy>();
		try
		{
			XMLWriter XML = new XMLWriter();
			XML.setNameSpace("DAV:", "D");
			XML.setNameSpace("urn:ietf:params:xml:ns:caldav", "C");

			XML.addChildElement("C:calendar-query");
			XML.addChildElement("D:prop");
			XML.addProperty("D:getetag");

			XML.addChildElement("C:calendar-data");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VCALENDAR");
			XML.addChildElement("C:comp");
			XML.addAttribute("name", "VFREEBUSY");
			XML.closeElement();
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.addChildElement("C:filter");
			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VCALENDAR");

			XML.addChildElement("C:comp-filter");
			XML.addAttribute("name", "VFREEBUSY");
			XML.addChildElement("C:time-range");
			XML.addAttribute("start", DateTime.getUTCTime(p.getStart()));
			XML.addAttribute("end", DateTime.getUTCTime(p.getEnd()));
			XML.closeElement();
			XML.closeElement();

			XML.closeElement();
			XML.closeElement();

			XML.closeElement();

			this._conn.setPath(path);
			this._conn.setContent(XML.toString());
			this._conn.connect(HTTPConnection.REPORT);
			if (this._conn.getResponseCode() != 207)
			{
				throw new Exception(this._conn.getResponseCode() + " - " + this._conn.getResponseMessage());
			}

			DocumentBuilderFactory _dbf = DocumentBuilderFactory.newInstance();
			_dbf.setNamespaceAware(true);
			DocumentBuilder _db = _dbf.newDocumentBuilder();
			Document document = _db.parse(new InputSource(new ByteArrayInputStream(this._conn.getContent())));

			Element root = document.getDocumentElement();
			if ("multistatus".equals(root.getLocalName()))
			{
				for (Node response : XMLHelper.getChildElements(root, "response"))
				{
					Node n = XMLHelper.findFirstSubElement(response, "calendar-data");
					if (n != null)
					{
						VCalendar _tvc = provider.createCalendar(n.getTextContent());
						busy.add(_tvc.getFreeBusy());
					}
				}
			}
			return busy;
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new CalDAVClientException(_ex.getMessage());
		}
	}

	private Map<String, String> getPropertiesForNode(Node n, int deep)
	{
		Map<String, String> properties = new HashMap<String, String>();
		for (Node p : XMLHelper.getChildElements(n))
		{
			if (p.getNodeType() == Node.ELEMENT_NODE)
			{
				if (p.hasChildNodes())
				{
					properties.putAll(getPropertiesForNode(p, deep + 1));
				}
				else
				{
					StringBuilder _sb = new StringBuilder();
					Node parent = p.getParentNode();
					for (int i = deep; --i > 0; parent = parent.getParentNode())
					{
						_sb.insert(0, parent.getLocalName());
						if (i != 1)
						{
							_sb.insert(0, "/");
						}
					}
					if (_sb.length() > 0)
					{
						if (properties.containsKey(_sb.toString()))
						{
							properties.put(_sb.toString(),
									p.getLocalName().concat(",") + properties.get(_sb.toString()));
						}
						else
						{
							properties.put(_sb.toString(), p.getLocalName());
						}
					}
					else
					{
						properties.put(p.getLocalName(), "");
					}
				}
			}
			else if (p.getNodeType() == Node.TEXT_NODE || p.getNodeType() == Node.CDATA_SECTION_NODE)
			{
				StringBuilder _sb = new StringBuilder();
				Node parent = p.getParentNode();
				for (int i = deep; --i > 0; parent = parent.getParentNode())
				{
					_sb.insert(0, parent.getLocalName());
					if (i != 1)
					{
						_sb.insert(0, "/");
					}
				}
				properties.put(_sb.toString(), p.getNodeValue());
			}
		}
		return properties;
	}
}
