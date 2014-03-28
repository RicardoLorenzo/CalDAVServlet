package com.whitebearsolutions.caldav.client;

public class CalDAVClientException extends Exception
{
	private static final long serialVersionUID = 1L;

	public CalDAVClientException()
	{
		super();
	}

	public CalDAVClientException(String message)
	{
		super(message);
	}

	public CalDAVClientException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public CalDAVClientException(Throwable cause)
	{
		super(cause);
	}
}
