package com.whitebearsolutions.caldav;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;

public interface CalDAVMethod
{
	void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp) throws IOException,
			LockException;
}
