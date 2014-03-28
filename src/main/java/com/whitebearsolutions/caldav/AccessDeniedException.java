package com.whitebearsolutions.caldav;

public class AccessDeniedException extends CalDAVException
{
	private static final long serialVersionUID = 1L;

	public AccessDeniedException()
	{
		super();
	}

	public AccessDeniedException(String message)
	{
		super(message);
	}

	public AccessDeniedException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public AccessDeniedException(Throwable cause)
	{
		super(cause);
	}
}
