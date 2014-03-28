package com.whitebearsolutions.caldav.locking;

import com.whitebearsolutions.caldav.session.CalDAVTransaction;

public interface ResourceLocks
{
	/**
	 * Intenta bloquear el recurso en "path".
	 * 
	 * @param transaction
	 * @param path
	 *            recurso a bloquear
	 * @param owner
	 *            propietario del bloqueo
	 * @param exclusive
	 *            si el bloqueo es exclusivo (o compartido)
	 * @param depth
	 *            la profundidad
	 * @param timeout
	 *            duraci&oacute;n del bloqueo en segundos.
	 * @return true if the resource at path was successfully locked, false if an
	 *         existing lock prevented this
	 * @throws LockException
	 */
	boolean lock(CalDAVTransaction transaction, String path, String owner, boolean exclusive, int depth, int timeout,
			boolean temporary) throws LockException;

	/**
	 * Desbloquea el recursos "id" (y todos las subcarpetas si existen) que
	 * tengan el mismo propietario
	 * 
	 * @param transaction
	 * @param id
	 *            identificador del recurso a desbloquear
	 * @param owner
	 *            el propietario del recurso a desbloquear
	 */
	boolean unlock(CalDAVTransaction transaction, String id, String owner);

	/**
	 * Desbloquea los recursos en "path" (y todos las subcarpetas si existen)
	 * que tengan el mismo propietario
	 * 
	 * @param transaction
	 * @param path
	 *            ruta del recurso a desbloquear
	 * @param owner
	 *            el propietario del recurso a desbloquear
	 */
	void unlockTemporaryLockedObjects(CalDAVTransaction transaction, String path, String owner);

	/**
	 * Borra los <code>LockedObject</code> cuyo timeout haya expirado
	 * 
	 * @param transaction
	 * @param temporary
	 *            verifica los timeout en bloqueos temporales o reales
	 */
	void checkTimeouts(CalDAVTransaction transaction, boolean temporary);

	/**
	 * Intenta bloquear el recurso en "path" exclusivamente
	 * 
	 * @param transaction
	 *            Transaction
	 * @param path
	 *            ruta del recurso a bloquear
	 * @param owner
	 *            propietario del bloqueo
	 * @param depth
	 *            depth
	 * @param timeout
	 *            duraci&oacute;n en segundos.
	 * @return verdadero si el recurso en la ruta ha sido bloqueado, falso si si
	 *         existe un bloqueo que lo previene
	 * @throws LockException
	 */
	boolean exclusiveLock(CalDAVTransaction transaction, String path, String owner, int depth, int timeout)
			throws LockException;

	/**
	 * Intenta bloquear el recurso en "path" de forma compartida (shared)
	 * 
	 * @param transaction
	 *            Transaction
	 * @param path
	 *            ruta del recurso a bloquear
	 * @param owner
	 *            propietario del bloqueo
	 * @param depth
	 *            depth
	 * @param timeout
	 *            duraci&oacute;n en segundos.
	 * @return verdadero si el recurso en la ruta ha sido bloqueado, falso si si
	 *         existe un bloqueo que lo previene
	 * @throws LockException
	 */
	boolean sharedLock(CalDAVTransaction transaction, String path, String owner, int depth, int timeout)
			throws LockException;

	/**
	 * Obtiene el <code>LockedObject</code> correspondiente a un identificador
	 * 
	 * @param transaction
	 * @param id
	 *            identificador para solicitar el recurso
	 * @return LockedObject o <code>null</code> si no existe un
	 *         <code>LockedObject</code> con ese identificador
	 */
	LockedObject getLockedObjectByID(CalDAVTransaction transaction, String id);

	/**
	 * Obtiene el <code>LockedObject</code> correspondiente a la ruta
	 * 
	 * @param transaction
	 * @param path
	 *            ruta al recurso
	 * @return LockedObject o <code>null</code> si no existe un
	 *         <code>LockedObject</code> en esa ruta
	 */
	LockedObject getLockedObjectByPath(CalDAVTransaction transaction, String path);

	/**
	 * Obtiene el <code>LockedObject</code> temporal correspondiente a un
	 * identificador
	 * 
	 * @param transaction
	 * @param id
	 *            identificador para solicitar el recurso
	 * @return LockedObject o <code>null</code> si no existe un
	 *         <code>LockedObject</code> con ese identificador
	 */
	LockedObject getTempLockedObjectByID(CalDAVTransaction transaction, String id);

	/**
	 * Obtiene el <code>LockedObject</code> temporal correspondiente a la ruta
	 * 
	 * @param transaction
	 * @param path
	 *            ruta al recurso
	 * @return LockedObject o <code>null</code> si no existe un
	 *         <code>LockedObject</code> en esa ruta
	 */
	LockedObject getTempLockedObjectByPath(CalDAVTransaction transaction, String path);
}
