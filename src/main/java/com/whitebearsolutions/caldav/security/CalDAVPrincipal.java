package com.whitebearsolutions.caldav.security;

import java.io.Serializable;
import java.security.Principal;

public class CalDAVPrincipal implements Principal, Serializable
{
	private static final long serialVersionUID = 1L;
	private String name;

	public CalDAVPrincipal(String name)
	{
		if (name.contains("/"))
		{
			name = name.substring(name.lastIndexOf("/") + 1);
		}
		this.name = name;
	}

	public String getName()
	{
		return this.name;
	}

	public boolean equals(Object o)
	{
		try
		{
			return ((CalDAVPrincipal) o).name.equalsIgnoreCase(this.name);
		}
		catch (Exception ex)
		{
			return false;
		}
	}
}
