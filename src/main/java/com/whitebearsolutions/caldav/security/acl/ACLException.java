package com.whitebearsolutions.caldav.security.acl;

import com.whitebearsolutions.caldav.CalDAVException;

public class ACLException extends CalDAVException
{
	public static final long serialVersionUID = 974921740198L;

	public ACLException()
	{
		super();
	}

	public ACLException(String message)
	{
		super(message);
	}

	public ACLException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ACLException(Throwable cause)
	{
		super(cause);
	}
}
