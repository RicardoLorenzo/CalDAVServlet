package com.whitebearsolutions.caldav;

public class ObjectAlreadyExistsException extends CalDAVException
{

	private static final long serialVersionUID = 1L;

	public ObjectAlreadyExistsException()
	{
		super();
	}

	public ObjectAlreadyExistsException(String message)
	{
		super(message);
	}

	public ObjectAlreadyExistsException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ObjectAlreadyExistsException(Throwable cause)
	{
		super(cause);
	}
}
