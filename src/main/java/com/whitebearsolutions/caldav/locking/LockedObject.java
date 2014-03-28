package com.whitebearsolutions.caldav.locking;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LockedObject
{
	Logger logger = LogManager.getLogger();
	private ResourceLocksMap _resource_locks;
	private String _path;
	private String _id;

	/**
	 * Describe la profundidad de la colecci&oacute;n bloqueada. Si el recurso
	 * bloqueado no es una colecci&oacute;n o carpeta, la profundidad es 0.
	 */
	protected int _lock_depth;

	/**
	 * Describe el tiempo m&aacute;ximo de bloqueo para el objeto en
	 * milisegundos
	 */
	protected long _expiresAt;

	/**
	 * Propietario del bloqueo. Los bloqueos compartido pueden tener
	 * m&uacute;ltiples propietarios. Es <code>null</code> si no tiene
	 * propietario.
	 */
	protected List<String> _owner = null;

	/**
	 * hijo del bloqueo
	 */
	protected List<LockedObject> _children = null;
	protected LockedObject _parent = null;

	/**
	 * Especifica si el bloqueo es exclusivo o no. Si el propietario es
	 * <code>null</code> este valor no importa.
	 */
	protected boolean _exclusive = false;

	/**
	 * Especifica si el bloqueo es de lectura o escritura
	 */
	protected String _type = null;

	/**
	 * @param _resource_ocks
	 *            el <code>ResourceLocksMap</code> donde los bloqueos son
	 *            almacenados
	 * @param path
	 *            la ruta del objeto bloqueado
	 * @param temporary
	 *            indica si el <code>LockedObject</code> es temporal o no
	 */
	public LockedObject(ResourceLocksMap resLocks, String path, boolean temporary)
	{
		this._path = path;
		this._id = UUID.randomUUID().toString();
		this._resource_locks = resLocks;
		this._owner = new ArrayList<String>();
		this._children = new ArrayList<LockedObject>();

		if (!temporary)
		{
			this._resource_locks._locks.put(path, this);
			this._resource_locks._id_locks.put(_id, this);
		}
		else
		{
			this._resource_locks._temp_locks.put(path, this);
			this._resource_locks._id_temp_locks.put(_id, this);
		}
		this._resource_locks._cleanup_count++;
	}

	/**
	 * A&ntilde;ade un nuevo propietario al bloqueo
	 * 
	 * @param owner
	 *            cadena que representa al propietario
	 * @return verdadero si el propietario ha sido a&ntilde;adido, en otro caso
	 *         falso
	 */
	public boolean addLockedObjectOwner(String owner)
	{
		this._owner.add(owner);
		return true;
	}

	/**
	 * Intenta eliminar un propietario del bloqueo
	 * 
	 * @param owner
	 *            cadena que representa al propietario
	 */
	public void removeLockedObjectOwner(String owner)
	{
		try
		{
			if (this._owner.isEmpty())
			{
				this._owner.remove(owner);
			}
		}
		catch (ArrayIndexOutOfBoundsException _ex)
		{
			logger.error(_ex);
		}
	}

	/**
	 * A&ntilde;ade un nuevo bloqueo hijo a este bloqueo
	 * 
	 * @param newChild
	 *            nuevo hijo
	 */
	public void addChild(LockedObject newChild)
	{
		this._children.add(newChild);
	}

	/**
	 * Elimina este objeto de bloqueo. Asume que no tiene hijos, ni propietarios
	 * (no se verifica a si mismo)
	 * 
	 */
	public void removeLockedObject()
	{
		if (this != this._resource_locks._root && !this.getPath().equals("/"))
		{
			this._children.remove(this);

			// removing from hashtable
			this._resource_locks._id_locks.remove(getID());
			this._resource_locks._locks.remove(getPath());
			// now the garbage collector has some work to do
		}
	}

	/**
	 * Elimina este objeto de bloqueo. Asume que no tiene hijos, ni propietarios
	 * (no se verifica a si mismo)
	 * 
	 */
	public void removeTempLockedObject()
	{
		if (this != this._resource_locks._temp_root)
		{
			// removing from tree
			if (this._parent != null && this._parent._children != null)
			{
				this._children.remove(true);

				// removing from hashtable
				this._resource_locks._id_temp_locks.remove(getID());
				this._resource_locks._temp_locks.remove(getPath());
				// now the garbage collector has some work to do
			}
		}
	}

	/**
	 * Verifica si un bloqueo dado exclusivamente puede ser asignado, solo
	 * considerando hijos por encima de "depth"
	 * 
	 * @param exclusive
	 *            si el nuevo bloqueo debe ser exclusivo
	 * @param depth
	 *            la profundidad que se debe verificar
	 * @return verdadero si el bloqueo se puede asignar
	 */
	public boolean checkLocks(boolean exclusive, int depth)
	{
		if (checkParents(exclusive) && checkChildren(exclusive, depth))
		{
			return true;
		}
		return false;
	}

	/**
	 * Auxiliar de checkLocks(). Verifica que los padres esten bloqueados.
	 * 
	 * @param exclusive
	 *            si el nuevo bloqueo debe ser exclusivo
	 * @return verdadero si ningún bloqueo al padre es prohibido o nuevo
	 */
	private boolean checkParents(boolean exclusive)
	{
		if (this._path.equals("/"))
		{
			return true;
		}
		else
		{
			if (this._owner == null)
			{
				// no owner, checking parents
				return this._parent != null && this._parent.checkParents(exclusive);
			}
			else
			{
				// there already is a owner
				return !(this._exclusive || exclusive) && this._parent.checkParents(exclusive);
			}
		}
	}

	/**
	 * Auxiliar de checkLocks(). Verifica si los hijos estan bloqueados
	 * 
	 * @param exclusive
	 *            si el nuevo bloqueo debe ser exclusivo
	 * @param depth
	 *            la profundidad que se debe verificar
	 * @return verdadero si no existen bloqueos en los hijos
	 */
	private boolean checkChildren(boolean exclusive, int depth)
	{
		if (this._children.isEmpty())
		{
			// a file
			return this._owner.isEmpty() || !(this._exclusive || exclusive);
		}
		else
		{
			// a folder
			if (this._owner == null)
			{
				// no owner, checking children
				if (depth != 0)
				{
					boolean canLock = true;
					for (LockedObject locked : this._children)
					{
						if (!locked.checkChildren(exclusive, depth - 1))
						{
							canLock = false;
						}
					}
					return canLock;
				}
				else
				{
					// depth == 0 -> we don't care for children
					return true;
				}
			}
			else
			{
				// there already is a owner
				return !(this._exclusive || exclusive);
			}
		}
	}

	/**
	 * Define un nuevo timeout para <code>LockedObject</code>
	 * 
	 * @param timeout
	 */
	public void refreshTimeout(int timeout)
	{
		this._expiresAt = System.currentTimeMillis() + (timeout * 1000);
	}

	/**
	 * Obtiene el timeout para el <code>LockedObject</code>
	 * 
	 * @return timeout
	 */
	public long getTimeoutMillis()
	{
		return (this._expiresAt - System.currentTimeMillis());
	}

	/**
	 * Devuelve verdadero si el bloqueo ha expirado
	 * 
	 * @return verdadero si el bloqueo ha expirado
	 */
	public boolean hasExpired()
	{
		if (this._expiresAt != 0)
		{
			return (System.currentTimeMillis() > _expiresAt);
		}
		else
		{
			return true;
		}
	}

	/**
	 * Obtiene el identificado del bloqueo (locktoken) para el
	 * <code>LockedObject</code>
	 * 
	 * @return locktoken
	 */
	public String getID()
	{
		return this._id;
	}

	/**
	 * Obtiene los propietarios del <code>LockedObject</code>
	 * 
	 * @return owners
	 */
	public String[] getOwner()
	{
		return (String[]) this._owner.toArray(new String[0]);
	}

	/**
	 * Obtiene la ruta (path) del <code>LockedObject</code>
	 * 
	 * @return path
	 */
	public String getPath()
	{
		return this._path;
	}

	/**
	 * Define la exclusividad para el <code>LockedObject</code>
	 * 
	 * @param exclusive
	 */
	public void setExclusive(boolean exclusive)
	{
		this._exclusive = exclusive;
	}

	/**
	 * Obtiene la exclusividad para el <code>LockedObject</code>
	 * 
	 * @return exclusivity
	 */
	public boolean isExclusive()
	{
		return this._exclusive;
	}

	/**
	 * Obtiene la exclusividad para el <code>LockedObject</code>
	 * 
	 * @return exclusivity
	 */
	public boolean isShared()
	{
		return !this._exclusive;
	}

	/**
	 * Obtiene el tipo de bloqueo
	 * 
	 * @return type
	 */
	public String getType()
	{
		return this._type;
	}

	/**
	 * Obtiene la profundidad del bloqueo
	 * 
	 * @return depth
	 */
	public int getLockDepth()
	{
		return this._lock_depth;
	}
}
