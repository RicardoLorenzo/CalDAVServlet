package com.whitebearsolutions.caldav;

public class UnauthenticatedException extends CalDAVException
{

	private static final long serialVersionUID = 1L;

	public UnauthenticatedException()
	{
		super();
	}

	public UnauthenticatedException(String message)
	{
		super(message);
	}

	public UnauthenticatedException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public UnauthenticatedException(Throwable cause)
	{
		super(cause);
	}
}