package com.whitebearsolutions.caldav.method;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.CalDAVMethod;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;

public class NOT_IMPLEMENTED implements CalDAVMethod
{
	public NOT_IMPLEMENTED()
	{
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException
	{
		resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}
}
