package com.whitebearsolutions.caldav.method;

import com.whitebearsolutions.caldav.store.StoredObject;

public abstract class CalDAVMethods extends CalDAVAbstractMethod
{

	private static final String NULL_RESOURCE_METHODS_ALLOWED = "OPTIONS, MKCOL, PUT, PROPFIND, LOCK, UNLOCK";

	private static final String RESOURCE_METHODS_ALLOWED = "OPTIONS, GET, HEAD, POST, DELETE, TRACE"
			+ ", PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, ACL, REPORT";

	private static final String FOLDER_METHOD_ALLOWED = ", PUT";

	private static final String LESS_ALLOWED_METHODS = "OPTIONS, MKCOL, PUT";

	/**
	 * Determines the allows methods for the resource.
	 * 
	 * @param so
	 *            StoredObject the object to determine the allows methods for.
	 * @return all allowed methods separated by commas
	 */
	protected static String determineMethodsAllowed(StoredObject so)
	{
		if (so != null)
		{
			if (so.isNullResource())
			{
				return NULL_RESOURCE_METHODS_ALLOWED;
			}
			else if (so.isFolder())
			{
				return RESOURCE_METHODS_ALLOWED + FOLDER_METHOD_ALLOWED;
			}
			return RESOURCE_METHODS_ALLOWED;
		}
		return LESS_ALLOWED_METHODS;
	}
}
