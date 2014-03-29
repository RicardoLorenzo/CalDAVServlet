/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ricardolorenzo.network.http.caldav;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.network.http.caldav.locking.ResourceLocksMap;
import com.ricardolorenzo.network.http.caldav.method.ACL;
import com.ricardolorenzo.network.http.caldav.method.COPY;
import com.ricardolorenzo.network.http.caldav.method.DELETE;
import com.ricardolorenzo.network.http.caldav.method.GET;
import com.ricardolorenzo.network.http.caldav.method.HEAD;
import com.ricardolorenzo.network.http.caldav.method.LOCK;
import com.ricardolorenzo.network.http.caldav.method.MKCALENDAR;
import com.ricardolorenzo.network.http.caldav.method.MKCOL;
import com.ricardolorenzo.network.http.caldav.method.MOVE;
import com.ricardolorenzo.network.http.caldav.method.NOT_IMPLEMENTED;
import com.ricardolorenzo.network.http.caldav.method.OPTIONS;
import com.ricardolorenzo.network.http.caldav.method.PROPFIND;
import com.ricardolorenzo.network.http.caldav.method.PROPPATCH;
import com.ricardolorenzo.network.http.caldav.method.PUT;
import com.ricardolorenzo.network.http.caldav.method.REPORT;
import com.ricardolorenzo.network.http.caldav.method.UNLOCK;
import com.ricardolorenzo.network.http.caldav.session.CalDAVTransaction;
import com.ricardolorenzo.network.http.caldav.store.CalDAVStore;

/**
 * 
 * @author Ricardo Lorenzo
 * 
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
 * security-provider
 *  - allows you to provide an external security provider to let 
 *  CalDAVServlet have access to the currently logged in user.
 *  If no security-provider is specified then the default
 *  servlet provider will be used (e.g. req.getUserPrinciple();
 *  
 *  Pass the fully qualified class to your security provider implementation.
 * 
 */

public class CalDAVServlet extends HttpServlet {
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final long serialVersionUID = 7073432765018098252L;
    
	private static final String SECURITY_PROVIDER = "security-provider";


    /**
     * MD5 message digest provider.
     */
    protected static MessageDigest MD5;

    private ResourceLocksMap resourceLocks;
    private CalDAVStore store;
    private Map<String, CalDAVMethod> httpMethods;
    
    public static SecurityProvider securityProvider;

    /**
     * CalDAVServlet constructor
     */
    public CalDAVServlet() {
        this.resourceLocks = new ResourceLocksMap();
        this.httpMethods = new HashMap<String, CalDAVMethod>();

        try {
            MD5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
        	logger.error("MD5", e);
            throw new IllegalStateException();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    public void init(ServletConfig conf) throws ServletException {
        boolean lazyFolderCreation = false;
        int no_content_length_headers = 0;
        String instead_of_404 = null;
        
		initProvider(conf);
		
        String initParameter = conf.getInitParameter("store");
		if (initParameter == null) {
            throw new ServletException("store parameter not found");
        }
        String default_index_file = conf.getInitParameter("default-index-file");
        if (conf.getInitParameter("lazy-folder-creation") != null) {
            try {
                if (Integer.parseInt(conf.getInitParameter("lazy-folder-creation")) == 1) {
                    lazyFolderCreation = true;
                }
            } catch (NumberFormatException e) {
                // nothing
            }
        }
        if (conf.getInitParameter("no-content-length-headers") != null) {
            try {
                no_content_length_headers = Integer.parseInt(conf.getInitParameter("no-content-length-headers"));
            } catch (NumberFormatException e) {
            	logger.warn("Invalid value for no-content-length-headers" + no_content_length_headers, e);
                // nothing
            }
        }
        if (conf.getInitParameter("instead-of-404") != null) {
            instead_of_404 = conf.getInitParameter("instead-of-404");
        }

        try {
    		@SuppressWarnings("unchecked")
            java.lang.reflect.Constructor<CalDAVStore> _c = (Constructor<CalDAVStore>) Class.forName(conf.getInitParameter("store")).getConstructor(new Class[]
    		{ ServletConfig.class });
    		this.store = (CalDAVStore) _c.newInstance(new Object[] { conf });

        } catch (ClassNotFoundException e) {
        	logger.error("class=" + initParameter, e);
            throw new ServletException("java class not found [" + initParameter + "]");
        } catch (Exception e) {
        	logger.error("class="+ initParameter, e);
            throw new ServletException("java class cannot be loaded [" + initParameter + "]: "
                    + e.toString());
        }

        CalDAVMimeType mimeType = new CalDAVMimeType() {
            public String getMimeType(String path) {
                return "text/xml";
            }
        };

        DELETE delete;
        COPY copy;
        MKCOL mkcol;

        addMethod("ACL", new ACL(this.store, this.resourceLocks));
        addMethod("GET", new GET(this.store, default_index_file, instead_of_404, this.resourceLocks, mimeType,
                no_content_length_headers));
        addMethod("HEAD", new HEAD(this.store, default_index_file, instead_of_404, this.resourceLocks, mimeType,
                no_content_length_headers));
        delete = (DELETE) addMethod("DELETE", new DELETE(this.store, this.resourceLocks));
        copy = (COPY) addMethod("COPY", new COPY(this.store, this.resourceLocks, delete));
        addMethod("LOCK", new LOCK(this.store, this.resourceLocks));
        addMethod("UNLOCK", new UNLOCK(this.store, this.resourceLocks));
        addMethod("MOVE", new MOVE(this.resourceLocks, delete, copy));
        mkcol = (MKCOL) addMethod("MKCOL", new MKCOL(this.store, this.resourceLocks));
        addMethod("OPTIONS", new OPTIONS(this.store, this.resourceLocks));
        addMethod("PUT", new PUT(this.store, this.resourceLocks, lazyFolderCreation));
        addMethod("PROPFIND", new PROPFIND(this.store, this.resourceLocks, mimeType));
        addMethod("PROPPATCH", new PROPPATCH(this.store, this.resourceLocks));
        addMethod("MKCALENDAR", new MKCALENDAR(this.store, this.resourceLocks, mkcol));
        addMethod("REPORT", new REPORT(this.store, this.resourceLocks));
        addMethod("*", new NOT_IMPLEMENTED());
    }

    private CalDAVMethod addMethod(String method_name, CalDAVMethod method) {
        this.httpMethods.put(method_name, method);
        return method;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest,
     * javax.servlet.http.HttpServletResponse)
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String methodName = req.getMethod();
        CalDAVTransaction transaction = null;
        boolean rollback = false;

        try {
            transaction = this.store.begin(CalDAVServlet.securityProvider.getUserPrincipal(req));
            rollback = true;
            this.store.checkAuthentication(transaction);
            resp.setStatus(CalDAVResponse.SC_OK);

            try {
                CalDAVMethod method = this.httpMethods.get(methodName);
                if (method == null) {
                    method = this.httpMethods.get("*");
                }
                method.execute(transaction, req, resp);
                this.store.commit(transaction);
                rollback = false;
            } catch (IOException e) { 
            	logger.error("methodName=" + methodName, e);
                resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
                this.store.rollback(transaction);
                throw new ServletException(e);
            }
        } catch (UnauthenticatedException e) {
            resp.sendError(CalDAVResponse.SC_FORBIDDEN);
        } catch (Exception e) {
        	logger.error("methodName=" + methodName, e);
            throw new ServletException(e);
        } finally {
            if (rollback) {
                this.store.rollback(transaction);
            }
        }
    }
    
    /**
	 * Instantiates a Calendar Provider from the servlet parameter security-provider
	 * @throws ServletException 
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	private void initProvider(ServletConfig conf) throws ServletException 
	{
		String className = conf.getInitParameter(SECURITY_PROVIDER);
	
		Class<SecurityProvider> providerClass;
		try
		{
			providerClass = (Class<SecurityProvider>) Class.forName(className);
			CalDAVServlet.securityProvider = providerClass.newInstance();
		}
		catch (ClassNotFoundException e)
		{
			logger.error("className=" + className, e);
			throw new ServletException(e);
		}
		catch (InstantiationException e)
		{
			logger.error("className=" + className, e);
			throw new ServletException(e);
		}
		catch (IllegalAccessException e)
		{
			logger.error("className=" + className, e);
			throw new ServletException(e);
		}
		
		if (securityProvider == null)
		{
			logger.warn("Using the default security provider");
			securityProvider = new DefaultSecurityProviderImpl();
		}
	
	}

}