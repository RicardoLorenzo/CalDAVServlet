package com.whitebearsolutions.caldav.security.acl;

import java.security.Principal;
import java.text.Collator;
import java.util.Collection;
import java.util.Locale;
import java.util.TreeMap;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;

public class CalDAVPrivilegeCollection
{
	private Principal owner;
	private TreeMap<String, CalDAVPrivilege> privileges;

	protected CalDAVPrivilegeCollection()
	{
		this.privileges = new TreeMap<String, CalDAVPrivilege>(Collator.getInstance(Locale.getDefault() ));
	}

	public CalDAVPrivilegeCollection(Principal principal)
	{
		this.owner = principal;
		this.privileges = new TreeMap<String, CalDAVPrivilege>(Collator.getInstance(Locale.getDefault()));
	}

	public void checkPrincipalPrivilege(Principal principal, String name) throws AccessDeniedException
	{
		if (!CalDAVPrivilege.getSupportedPrivileges().keySet().contains(name))
		{
			throw new AccessDeniedException(name);
		}

		if (this.owner == null)
		{
			throw new AccessDeniedException(name);
		}

		if ("all".equals(this.owner.getName()))
		{
			return;
		}

		if (this.owner.getName().equals(principal.getName()))
		{
			return;
		}

		if (!this.privileges.containsKey(principal.getName()))
		{
			throw new AccessDeniedException(name);
		}

		CalDAVPrivilege p = this.privileges.get(principal.getName());
		if (p.hasDeniedPrivilege(name))
		{
			throw new AccessDeniedException(name);
		}

		if (!p.hasGrantedPrivilege(name))
		{
			throw new AccessDeniedException(name);
		}
	}

	public Principal getOwner()
	{
		return this.owner;
	}

	public Collection<CalDAVPrivilege> getAllPrivileges()
	{
		return this.privileges.values();
	}

	public CalDAVPrivilege getPrincipalPrivilege(Principal principal)
	{
		if (this.owner != null && this.owner.getName().equals(principal.getName()))
		{
			CalDAVPrivilege p = new CalDAVPrivilege(this.owner);
			p.setGrantPrivilege("all");
			return p;
		}
		if (this.privileges.containsKey(principal.getName()))
		{
			return this.privileges.get(principal.getName());
		}
		return new CalDAVPrivilege(principal);
	}

	protected void setOwner(Principal principal)
	{
		this.owner = principal;
	}

	public void setPrivilege(CalDAVPrivilege privilege) throws CalDAVException, AccessDeniedException
	{
		if (privilege.getGrantedPrivileges().isEmpty() && privilege.getDeniedPrivileges().isEmpty())
		{
			this.privileges.remove(privilege.getPrincipalName());
		}
		else
		{
			this.privileges.put(privilege.getPrincipalName(), privilege);
		}
	}

	public void removePrincipalPrivilege(Principal principal)
	{
		this.privileges.remove(principal.getName());
	}
}
