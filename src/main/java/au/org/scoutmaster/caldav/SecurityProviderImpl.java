package au.org.scoutmaster.caldav;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

import com.ricardolorenzo.network.http.caldav.SecurityProvider;

public class SecurityProviderImpl implements SecurityProvider
{

	@Override
	public Principal getUserPrincipal(HttpServletRequest req)
	{
		
		return new ScoutmasterUserPrincipal();
	}

}
