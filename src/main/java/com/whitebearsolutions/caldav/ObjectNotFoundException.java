package com.whitebearsolutions.caldav;

public class ObjectNotFoundException extends CalDAVException
{

	private static final long serialVersionUID = 1L;

	public ObjectNotFoundException()
	{
		super();
	}

	public ObjectNotFoundException(String message)
	{
		super(message);
	}

	public ObjectNotFoundException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ObjectNotFoundException(Throwable cause)
	{
		super(cause);
	}
}
