package com.whitebearsolutions.caldav.session;

import java.security.Principal;

public class CalDAVITransaction implements CalDAVTransaction
{
	private Principal principal;

	public CalDAVITransaction(Principal principal)
	{
		this.principal = principal;
	}

	public Principal getPrincipal()
	{
		return this.principal;
	}

}
