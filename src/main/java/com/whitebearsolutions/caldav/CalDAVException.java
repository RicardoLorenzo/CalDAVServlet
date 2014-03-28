package com.whitebearsolutions.caldav;

public class CalDAVException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public CalDAVException()
	{
		super();
	}

	public CalDAVException(String message)
	{
		super(message);
	}

	public CalDAVException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public CalDAVException(Throwable cause)
	{
		super(cause);
	}
}
