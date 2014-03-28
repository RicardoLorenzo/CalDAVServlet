package com.whitebearsolutions.xml;

import java.util.Calendar;
import java.util.Date;

public class Period
{
	private Date start;
	private Date end;

	public Period(Calendar start, Calendar end)
	{
		this.start = start.getTime();
		this.end = end.getTime();
	}

	public Date getEnd()
	{
		return end;
	}

	public Date getStart()
	{
		return start;
	}

}
