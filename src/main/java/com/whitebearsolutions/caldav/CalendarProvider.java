package com.whitebearsolutions.caldav;

import java.io.ByteArrayInputStream;
import java.security.Principal;

import com.whitebearsolutions.xml.VCalendar;

public interface CalendarProvider
{
	VCalendar createCalendar();

	String getVersion();

	String getProdId();

	VCalendar createCalendar(String textContent);

	VCalendar createCalendar(ByteArrayInputStream byteArrayInputStream);

	Principal getUserPrincipal();
	
}
