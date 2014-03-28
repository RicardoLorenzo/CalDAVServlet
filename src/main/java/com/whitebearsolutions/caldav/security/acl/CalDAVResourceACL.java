package com.whitebearsolutions.caldav.security.acl;

import java.util.HashMap;
import java.util.List;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;

public interface CalDAVResourceACL
{
	/**
	 * Obtiene el objeto <code>CalDAVPrivilegeCollection</code> que contiene
	 * toda la informaci&oacute;n de ACL's asociada recurso.
	 * 
	 * @param transaction
	 * @throws ACLException
	 */
	CalDAVPrivilegeCollection getPrivilegeCollection();

	/**
	 * Obtiene el conjunto de privilegios disponibles.
	 * 
	 * @param transaction
	 * @throws ACLException
	 */
	HashMap<String, String> getSupportedPrivilegeSet();

	/**
	 * Obtiene el conjunto de URL's que identifican los usuarios o grupos
	 * disponibles en el servidor.
	 * 
	 * @param transaction
	 * @throws ACLException
	 */
	List<String> getPrincipalCollectionSet();

	/**
	 * Define el objeto <code>CalDAVPrivilegeCollection</code> que contiene toda
	 * la informaci&oacute;n de ACL's asociada al recurso.
	 * 
	 * @param transaction
	 * @throws ACLException
	 */
	void setPrivilegeCollection(CalDAVTransaction transaction, CalDAVPrivilegeCollection collection)
			throws ACLException, AccessDeniedException;

	void removeCollection(CalDAVTransaction transaction) throws CalDAVException;
}
