package com.ricardolorenzo.network.http.caldav;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

public interface SecurityProvider
{
	Principal getUserPrincipal(HttpServletRequest req);
	
}
