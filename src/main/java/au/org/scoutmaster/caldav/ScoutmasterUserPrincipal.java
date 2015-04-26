package au.org.scoutmaster.caldav;

import java.security.Principal;

public class ScoutmasterUserPrincipal implements Principal
{

	@Override
	public String getName()
	{
		// TODO: return the current logged in user.
		return "bsutton";
	}

}
