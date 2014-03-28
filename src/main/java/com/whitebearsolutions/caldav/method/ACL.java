/**
 * 
 */
package com.whitebearsolutions.caldav.method;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVResponse;
import com.whitebearsolutions.caldav.CalDAVServlet;
import com.whitebearsolutions.caldav.locking.LockException;
import com.whitebearsolutions.caldav.locking.ResourceLocksMap;
import com.whitebearsolutions.caldav.security.CalDAVPrincipal;
import com.whitebearsolutions.caldav.security.acl.CalDAVPrivilege;
import com.whitebearsolutions.caldav.security.acl.CalDAVPrivilegeCollection;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.caldav.store.StoredObject;
import com.whitebearsolutions.xml.URLEncoder;
import com.whitebearsolutions.xml.XMLHelper;

/**
 * @author Ricardo Lorenzo
 *
 */
public class ACL extends CalDAVAbstractMethod
{
	/**
	 * Array containing the safe characters set.
	 */
	protected static URLEncoder URL_ENCODER;

	private CalDAVStore _store;
	private ResourceLocksMap _resource_locks;
	private CalDAVResourceACL resource_acl;
	private int _depth;

	public ACL(CalDAVStore store, ResourceLocksMap resLocks)
	{
		this._store = store;
		this._resource_locks = resLocks;
	}

	public void execute(CalDAVTransaction transaction, HttpServletRequest req, HttpServletResponse resp)
			throws IOException, LockException
	{
		String path = getCleanPath(getRelativePath(req));
		String tempLockOwner = "ACL" + System.currentTimeMillis() + req.toString();
		this.resource_acl = this._store.getResourceACL(transaction, path);
		this._depth = getDepth(req);

		if (this._resource_locks.lock(transaction, path, tempLockOwner, false, this._depth, TEMP_TIMEOUT, TEMPORARY))
		{
			StoredObject so = null;
			try
			{
				this.resource_acl.getPrivilegeCollection().checkPrincipalPrivilege(CalDAVServlet.provider.getUserPrincipal(req), "write-acl");
				so = this._store.getStoredObject(transaction, path);
				if (so == null)
				{
					resp.setContentType("text/xml; charset=UTF-8");
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
					return;
				}

				path = getCleanPath(getRelativePath(req));

				if (req.getContentLength() > 0)
				{
					DocumentBuilder documentBuilder = getDocumentBuilder();
					try
					{
						Document document = documentBuilder.parse(new InputSource(req.getInputStream()));
						Element root = document.getDocumentElement();

						CalDAVPrivilegeCollection collection = this.resource_acl.getPrivilegeCollection();
						CalDAVResourceACL calendarResource = null;

						if (!"acl".equals(root.getLocalName()))
						{
							resp.sendError(CalDAVResponse.SC_BAD_REQUEST);
						}
						else
						{

							if (so.isFolder())
							{
								String calendarPath = path;
								if (!calendarPath.endsWith("/"))
								{
									calendarPath = calendarPath.concat("/");
								}
								calendarPath += "calendar.ics";

								if (this._store.getStoredObject(transaction, calendarPath) != null)
								{
									calendarResource = this._store.getResourceACL(transaction, calendarPath);
								}
							}
							for (Node n : XMLHelper.getChildElements(root, "ace"))
							{
								CalDAVPrivilege privilege = new CalDAVPrivilege();
								for (Node nn : XMLHelper.getChildElements(n))
								{
									if ("principal".equals(nn.getLocalName()))
									{
										Node nnn = XMLHelper.firstSubElement(nn, "href");
										if (nnn != null)
										{
											privilege.setPrincipal(new CalDAVPrincipal(nnn.getTextContent()));
										}
									}
									else if ("grant".equals(nn.getLocalName()))
									{
										Node nnn = XMLHelper.firstSubElement(nn, "privilege");
										if (nnn != null)
										{
											privilege.setGrantPrivilege(nnn.getFirstChild().getLocalName());
										}
									}
									else if ("deny".equals(nn.getLocalName()))
									{
										Node nnn = XMLHelper.firstSubElement(nn, "privilege");
										if (nnn != null)
										{
											privilege.setDenyPrivilege(nnn.getFirstChild().getLocalName());
										}
									}
								}

								CalDAVPrivilege priv = collection.getPrincipalPrivilege(privilege.getPrincipal());
								if (priv != null)
								{
									List<String> grants = priv.getGrantedPrivileges();
									for (String grant : privilege.getGrantedPrivileges())
									{
										if (!grants.contains(grant))
										{
											grants.add(grant);
										}
									}
									for (String deny : privilege.getDeniedPrivileges())
									{
										grants.remove(deny);
									}

									privilege.setGrantPrivileges(grants);
									privilege.removeAllDeniedPrivileges();
								}

								collection.setPrivilege(privilege);
							}
						}

						if (calendarResource != null)
						{
							calendarResource.setPrivilegeCollection(transaction, collection);
						}
						this.resource_acl.setPrivilegeCollection(transaction, collection);
					}
					catch (Exception _ex)
					{
						logger.error(_ex);
						resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
						return;
					}
				}
				resp.setStatus(CalDAVResponse.SC_OK);
			}
			catch (AccessDeniedException _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_FORBIDDEN);
			}
			catch (Exception _ex)
			{
				logger.error(_ex);
				resp.sendError(CalDAVResponse.SC_INTERNAL_SERVER_ERROR);
			}
			finally
			{
				this._resource_locks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
			}
		}
		else
		{
			Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
			errorList.put(path, CalDAVResponse.SC_LOCKED);
			sendReport(req, resp, errorList);
		}
	}
}
