package com.whitebearsolutions.caldav.security.acl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.whitebearsolutions.caldav.AccessDeniedException;
import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.security.CalDAVPrincipal;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;
import com.whitebearsolutions.caldav.store.CalDAVStore;
import com.whitebearsolutions.xml.XMLAttribute;
import com.whitebearsolutions.xml.XMLDB;
import com.whitebearsolutions.xml.XMLObject;

public class FileSystemResourceACL implements CalDAVResourceACL
{
	Logger logger = LogManager.getLogger();
	private CalDAVPrivilegeCollection privileges;
	private CalDAVStore store;
	private String path;
	private File _xml_file;
	private XMLObject resourceXMLObject;

	public FileSystemResourceACL(CalDAVStore store, CalDAVTransaction transaction, String path) throws CalDAVException
	{
		this.store = store;
		if (path == null || path.isEmpty())
		{
			throw new CalDAVException("invalid resource path");
		}

		this.path = path;
		File _f = new File(this.store.getRootPath() + this.path);
		if (_f.exists())
		{
			if (_f.isFile())
			{
				_f = _f.getParentFile();
			}

			if (_f == null || !_f.isDirectory())
			{
				throw new CalDAVException("can not determine the filesystem context");
			}

			this._xml_file = new File(_f.getAbsolutePath() + File.separator + ".acl.xml");
			if (!this._xml_file.exists())
			{
				this.privileges = new CalDAVPrivilegeCollection(transaction.getPrincipal());
				storePrivilegeCollection(transaction);
			}
			else
			{
				boolean newResource = true;
				try
				{
					XMLDB _db = new XMLDB(this._xml_file);
					this.privileges = new CalDAVPrivilegeCollection();

					for (XMLObject resource : _db.getObjects())
					{
						if (!resource.hasAttribute("path"))
						{
							continue;
						}
						else if (!this.path.equals(resource.getAttribute("path").getNodeValue()))
						{
							continue;
						}

						if (resource.hasAttribute("principal"))
						{
							this.privileges.setOwner(new CalDAVPrincipal(resource.getAttribute("principal")
									.getNodeValue()));
						}

						newResource = false;
						this.resourceXMLObject = resource;
						for (XMLObject o : resource.getObjects())
						{
							CalDAVPrivilege privilege = null;
							if (o.hasAttribute("principal"))
							{
								privilege = new CalDAVPrivilege(new CalDAVPrincipal(o.getAttribute("principal")
										.getNodeValue()));
							}
							else
							{
								privilege = new CalDAVPrivilege();
								privilege.setPrincipal(new CalDAVPrincipal("all"));
							}

							for (String pName : CalDAVPrivilege.getSupportedPrivileges().keySet())
							{
								if (o.hasAttribute(pName))
								{
									if ("grant".equals(o.getAttribute(pName).getNodeValue()))
									{
										privilege.setGrantPrivilege(pName);
									}
									else
									{
										privilege.setDenyPrivilege(pName);
									}
								}
							}

							this.privileges.setPrivilege(privilege);
						}
					}

					if (newResource)
					{
						this.privileges.setOwner(transaction.getPrincipal());
						storePrivilegeCollection(transaction);
					}
				}
				catch (Exception _ex)
				{
					logger.error(_ex);
					if (_ex instanceof NullPointerException)
					{
						throw (NullPointerException) _ex;
					}
					throw new CalDAVException(_ex.getMessage());
				}
			}
		}
		else
		{
			_f = _f.getParentFile();
			this._xml_file = new File(_f.getAbsolutePath() + File.separator + ".acl.xml");
			this.privileges = new CalDAVPrivilegeCollection(transaction.getPrincipal());
		}
	}

	public CalDAVPrivilegeCollection getPrivilegeCollection()
	{
		return this.privileges;
	}

	public List<String> getPrincipalCollectionSet()
	{
		return new ArrayList<String>();
	}

	public HashMap<String, String> getSupportedPrivilegeSet()
	{
		return CalDAVPrivilege.getSupportedPrivileges();
	}

	public void setPrivilegeCollection(CalDAVTransaction transaction, CalDAVPrivilegeCollection collection)
			throws ACLException, AccessDeniedException
	{
		this.privileges = collection;
		storePrivilegeCollection(transaction);
	}

	public void removeCollection(CalDAVTransaction transaction) throws NullPointerException, CalDAVException
	{
		this.privileges.checkPrincipalPrivilege(transaction.getPrincipal(), "write");
		removePrivilegeCollection();
	}

	private void storePrivilegeCollection(CalDAVTransaction transaction)
	{
		try
		{
			XMLDB _db = new XMLDB(this._xml_file);
			ArrayList<XMLObject> objects = new ArrayList<XMLObject>();
			if (this.resourceXMLObject == null)
			{
				this.resourceXMLObject = _db.createXMLObject();
				XMLAttribute a = new XMLAttribute("path");
				a.setValue(this.path);
				this.resourceXMLObject.addAttribute(a);
			}

			if (!this.resourceXMLObject.hasAttribute("principal"))
			{
				if (this.privileges.getOwner() == null)
				{
					this.privileges.setOwner(transaction.getPrincipal());
				}

				XMLAttribute a = new XMLAttribute("principal");
				a.setValue(this.privileges.getOwner().getName());
				this.resourceXMLObject.addAttribute(a);
			}

			for (CalDAVPrivilege privilege : this.privileges.getAllPrivileges())
			{
				XMLObject o = _db.createXMLObject();
				XMLAttribute a = new XMLAttribute("principal");
				a.setValue(privilege.getPrincipalName());
				o.addAttribute(a);
				for (String p : privilege.getGrantedPrivileges())
				{
					a = new XMLAttribute(p);
					a.setValue("grant");
					o.addAttribute(a);
				}
				for (String p : privilege.getDeniedPrivileges())
				{
					a = new XMLAttribute(p);
					a.setValue("deny");
					o.addAttribute(a);
				}
				objects.add(o);
			}

			this.resourceXMLObject.setObjects(objects);
			_db.updateObject(this.resourceXMLObject);
			_db.store();
		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			if (_ex instanceof NullPointerException)
			{
				throw (NullPointerException) _ex;
			}
			throw new CalDAVException(_ex.getMessage());
		}
	}

	private void removePrivilegeCollection() throws NullPointerException, CalDAVException
	{
		try
		{
			XMLDB _db = new XMLDB(this._xml_file);
			if (this.resourceXMLObject != null)
			{
				_db.removeObject(this.resourceXMLObject.getId());
				_db.store();
			}

		}
		catch (Exception _ex)
		{
			logger.error(_ex);
			if (_ex instanceof NullPointerException)
			{
				throw (NullPointerException) _ex;
			}
			throw new CalDAVException(_ex.getMessage());
		}
	}
}
