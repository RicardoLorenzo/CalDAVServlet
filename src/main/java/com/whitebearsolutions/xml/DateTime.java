package com.whitebearsolutions.xml;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DateTime
{
	private static Logger logger = LogManager.getLogger();
	private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS zzz";
	private static final SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT);
	private static final TimeZone utc = TimeZone.getTimeZone("UTC");

	public static String getUTCTime(Date start)
	{
		sdf.setTimeZone(utc);
		return sdf.format(start);
	}

	public static Calendar getCalendarFromString(Object object, String dateString)
	{
		Calendar cal = null;
		sdf.setTimeZone(utc);

		Date date;
		try
		{
			date = sdf.parse(dateString);
			cal = Calendar.getInstance();
			cal.setTime(date);

		}
		catch (ParseException e)
		{
			logger.error(e);
		}
		return cal;
	}

}
