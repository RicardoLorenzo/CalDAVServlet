package com.whitebearsolutions.caldav.locking;

import com.whitebearsolutions.caldav.CalDAVException;

public class LockException extends CalDAVException
{
	private static final long serialVersionUID = 1L;

	public LockException()
	{
		super();
	}

	public LockException(String message)
	{
		super(message);
	}

	public LockException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public LockException(Throwable cause)
	{
		super(cause);
	}
}
