package com.whitebearsolutions.caldav.store;

import java.util.Date;

/**
 * 
 * Representa a un objeto almacenado en el repositorio.
 * 
 * 
 * @author Ricardo Lorenzo
 */

public class StoredObject
{
	private boolean is_folder;
	private Date last_modified;
	private Date creation_date;
	private long content_length;

	private boolean is_null_ressource;

	/**
	 * Determina si el objeto es una carpeta o un recurso.
	 * 
	 * @return verdadero si el objeto es una carpeta
	 */
	public boolean isFolder()
	{
		return this.is_folder;
	}

	/**
	 * Determina si el objeto es una carpeta o un recurso.
	 * 
	 * @return verdadero si el objeto es un recurso
	 */
	public boolean isResource()
	{
		return !this.is_folder;
	}

	/**
	 * Define si el objeto es una carpeta o un recurso
	 * 
	 * @param value
	 *            true - carpeta ; false - recurso
	 */
	public void setFolder(boolean value)
	{
		this.is_folder = value;
	}

	/**
	 * Obtiene la fecha de la &uacute;ltima modificaci&oacute;n
	 * 
	 * @return &uacute;ltima modificaci&oacute;n Date
	 */
	public Date getLastModified()
	{
		return this.last_modified;
	}

	/**
	 * Define la fecha de la &uacute;ltima modificaci&oacute;n
	 * 
	 * @param d
	 *            fecha de la &uacute;ltima modificaci&oacute;n
	 */
	public void setLastModified(Date d)
	{
		this.last_modified = d;
	}

	/**
	 * Obtiene la fecha de creaci&oacute;n
	 * 
	 * @return fecha de creaci&oacute;n
	 */
	public Date getCreationDate()
	{
		return this.creation_date;
	}

	/**
	 * Define la fecha de creaci&oacute;n
	 * 
	 * @param date
	 *            fecha de creaci&oacute;n
	 */
	public void setCreationDate(Date date)
	{
		this.creation_date = date;
	}

	/**
	 * Obtiene el tama&ntilde;o del contenido del recurso
	 * 
	 * @return la fecha de creaci&oacute;n
	 */
	public long getResourceLength()
	{
		return this.content_length;
	}

	/**
	 * Define el tama&ntilde;o del contenido del recurso
	 * 
	 * @param l
	 *            el tama&ntilde;o del contenido del recurso
	 */
	public void setResourceLength(long l)
	{
		this.content_length = l;
	}

	/**
	 * Obtiene el estado del recurso
	 * 
	 * @return verdadero si el recurso est&aacute; en estado lock-null
	 */
	public boolean isNullResource()
	{
		return this.is_null_ressource;
	}

	/**
	 * Define el objeto como un recurso lack-null
	 * 
	 * @param f
	 *            verdadero para definir el recurso lock-null
	 */
	public void setNullResource(boolean f)
	{
		this.is_null_ressource = f;
		this.is_folder = false;
		this.creation_date = null;
		this.last_modified = null;
		// this.content = null;
		this.content_length = 0;
	}

}
