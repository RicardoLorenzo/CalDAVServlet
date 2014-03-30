package com.ricardolorenzo.network.http.caldav;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

public class DefaultSecurityProviderImpl implements SecurityProvider
{

	@Override
	public Principal getUserPrincipal(HttpServletRequest req)
	{
		return req.getUserPrincipal();
	}

}
