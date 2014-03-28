package au.com.scoutmaster;

import java.io.ByteArrayInputStream;
import java.security.Principal;

import com.whitebearsolutions.caldav.CalendarProvider;
import com.whitebearsolutions.xml.VCalendar;

public class CalendarProviderImpl implements CalendarProvider
{

	@Override
	public VCalendar createCalendar()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getVersion()
	{
		return "1.0";
	}

	@Override
	public String getProdId()
	{
		return "-//Brett Sutton//Scoutmaster 1.0//EN";
	}

	@Override
	public VCalendar createCalendar(String textContent)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VCalendar createCalendar(ByteArrayInputStream byteArrayInputStream)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Principal getUserPrincipal()
	{
		
		return new ScoutmasterUserPrincipal();
	}

}
