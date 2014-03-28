package com.whitebearsolutions.caldav;

/**
 * The core servlet that handles CalDAV requests.
 * 
 * A method is registered for each CalDAV request type.
 * 
 * To configure the servlet you need to provide the following servlet init-param's:
 * 
 * store - the fully qualified class to the calendar store.
 * 	Standard stores are:  
 * 		<code>com.whitebearsolutions.caldav.store.FileSystemStore</code>
 * 
 * lazy-folder-creation-on-put  - 
 * 	This should be 1 for lazy creation of 0 for immediate creation.
 * Defaults to 0
 * 
 * default-index-file - the default html file to return if the request is for a folder rather than an calendar object.
 * 
 * no-content-length-headers
 * 	This should be 1 if content length headers are to be suppressed.
 *  Defaults to 0 
 * 
 * instead-of-404
 *  Allows you to define a html page that is displayed rather than a 404. 
 *  Possibly this can be a URL to redirect to on the local system but I'm not certain.
 * 	
 * 
 */

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.method.ACL;
import com.whitebearsolutions.caldav.method.COPY;
import com.whitebearsolutions.caldav.method.DELETE;
import com.whitebearsolutions.caldav.method.GET;
import com.whitebearsolutions.caldav.method.HEAD;
import com.whitebearsolutions.caldav.method.LOCK;
import com.whitebearsolutions.caldav.method.MKCALENDAR;
import com.whitebearsolutions.caldav.method.MKCOL;
import com.whitebearsolutions.caldav.method.MOVE;
import com.whitebearsolutions.caldav.method.NOT_IMPLEMENTED;
import com.whitebearsolutions.caldav.method.OPTIONS;
import com.whitebearsolutions.caldav.method.PROPFIND;
import com.whitebearsolutions.caldav.method.PROPPATCH;
import com.whitebearsolutions.caldav.method.PUT;
import com.whitebearsolutions.caldav.method.REPORT;
import com.whitebearsolutions.caldav.method.UNLOCK;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;

public class CalDAVServlet extends HttpServlet
{
	Logger logger = LogManager.getLogger();
	private static final String CALENDAR_PROVIDER = "CalendarProvider";

	private static final long serialVersionUID = 1L;

	/**
	 * MD5 message digest provider.
	 */
	protected static MessageDigest MD5_HELPER;

	private ResourceLocksMap _resource_locks;
	private CalDAVStore _store;
	private HashMap<String, CalDAVMethod> _methods;
	
	public static CalendarProvider provider;

	public CalDAVServlet()
	{
	
		this._resource_locks = new ResourceLocksMap();
		this._methods = new HashMap<String, CalDAVMethod>();

		try
		{
			MD5_HELPER = MessageDigest.getInstance("MD5");
		}
		catch (NoSuchAlgorithmException _ex)
		{
			throw new IllegalStateException();
		}
	}

	/**
	 * Instantiates a Calendar Provider from the servlet parameter CALENDAR_PROVIDER
	 * @throws ServletException 
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	private void initProvider(ServletConfig conf) throws ServletException 
	{
		String className = conf.getInitParameter(CALENDAR_PROVIDER);
		
		Class<CalendarProvider> providerClass;
		try
		{
			providerClass = (Class<CalendarProvider>) Class.forName(className);
			CalDAVServlet.provider = providerClass.newInstance();

		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException e)
		{
			logger.error(e);
			throw new ServletException(e);
		}
	
	}

	public void init(ServletConfig conf) throws ServletException
	{
		boolean lazy_folder_creation_on_put = false;
		int no_content_length_headers = 0;
		String instead_of_404 = null;
		
		initProvider(conf);

		if (conf.getInitParameter("store") == null)
		{
			throw new ServletException("store parameter not found");
		}
		String default_index_file = conf.getInitParameter("default-index-file");
		if (conf.getInitParameter("lazy-folder-creation-on-put") != null)
		{
			try
			{
				if (Integer.parseInt(conf.getInitParameter("lazy-folder-creation-on-put")) == 1)
				{
					lazy_folder_creation_on_put = true;
				}
			}
			catch (NumberFormatException _ex)
			{
				logger.warn(_ex);
			}
		}
		if (conf.getInitParameter("no-content-length-headers") != null)
		{
			try
			{
				no_content_length_headers = Integer.parseInt(conf.getInitParameter("no-content-length-headers"));
			}
			catch (NumberFormatException _ex)
			{
				logger.warn(_ex);
			}
		}
		if (conf.getInitParameter("instead-of-404") != null)
		{
			instead_of_404 = conf.getInitParameter("instead-of-404");
		}

		try
		{
			@SuppressWarnings("unchecked")
			java.lang.reflect.Constructor<CalDAVStore> _c = (Constructor<CalDAVStore>) Class.forName(conf.getInitParameter("store")).getConstructor(new Class[]
			{ ServletConfig.class });
			this._store = (CalDAVStore) _c.newInstance(new Object[]
			{ conf });
		}
		catch (ClassNotFoundException _ex)
		{
			throw new ServletException("Cannot find class [" + conf.getInitParameter("store") + "] for the store");
		}
		catch (Exception _ex)
		{
			throw new ServletException("Could not load class [" + conf.getInitParameter("store") + "] for the store :"
					+ _ex.toString());
		}

		CalDAVMimeType mimeType = new CalDAVMimeType()
		{
			public String getMimeType(String path)
			{
				/*
				 * if(getServletContext() != null) {
				 * System.out.println("Servletcontext: " + getServletContext());
				 * return getServletContext().getMimeType(path); }
				 */
				return "text/xml";
			}
		};

		register("ACL", new ACL(this._store, this._resource_locks));
		register("GET", new GET(this._store, default_index_file, instead_of_404, this._resource_locks, mimeType,
				no_content_length_headers));
		register("HEAD", new HEAD(this._store, default_index_file, instead_of_404, this._resource_locks, mimeType,
				no_content_length_headers));
		DELETE _delete = (DELETE) register("DELETE", new DELETE(this._store, this._resource_locks));
		COPY _copy = (COPY) register("COPY", new COPY(this._store, this._resource_locks, _delete));
		register("LOCK", new LOCK(this._store, this._resource_locks));
		register("UNLOCK", new UNLOCK(this._store, this._resource_locks));
		register("MOVE", new MOVE(this._resource_locks, _delete, _copy));
		MKCOL _mkcol = (MKCOL) register("MKCOL", new MKCOL(this._store, this._resource_locks));
		register("OPTIONS", new OPTIONS(this._store, this._resource_locks));
		register("PUT", new PUT(this._store, this._resource_locks, lazy_folder_creation_on_put));
		register("PROPFIND", new PROPFIND(this._store, this._resource_locks, mimeType));
		register("PROPPATCH", new PROPPATCH(this._store, this._resource_locks));
		register("MKCALENDAR", new MKCALENDAR(this._store, this._resource_locks, _mkcol));
		register("REPORT", new REPORT(this._store, this._resource_locks));
		register("*NO*IMPL*", new NOT_IMPLEMENTED());
	}

	private CalDAVMethod register(String method_name, CalDAVMethod method)
	{
		this._methods.put(method_name, method);
		return method;
	}

	/**
	 * Maneja los m&eacute;todos especiales WebDAV
	 */
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{
		String method_name = req.getMethod();
		CalDAVTransaction transaction = null;
		boolean rollback = false;

		try
		{
			transaction = this._store.begin(provider.getUserPrincipal(req));
			rollback = true;
			this._store.checkAuthentication(transaction);
			resp.setStatus(CalDAVResponse.SC_OK);

			try
			{
				CalDAVMethod method = this._methods.get(method_name);
				if (method == null)
				{
					method = this._methods.get("*NO*IMPL*");
				}
				method.execute(transaction, req, resp);
				this._store.commit(transaction);
				rollback = false;
			}
			catch (IOException _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
				this._store.rollback(transaction);
				throw new ServletException(_ex);
			}
		}
		catch (UnauthenticatedException _ex)
		{
			resp.sendError(CalDAVResponse.SC_FORBIDDEN);
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			throw new ServletException(_ex);
		}
		finally
		{
			if (rollback)
			{
				this._store.rollback(transaction);
			}
		}
	}
}