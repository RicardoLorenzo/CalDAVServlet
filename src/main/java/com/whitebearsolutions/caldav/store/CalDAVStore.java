package com.whitebearsolutions.caldav.store;

import java.io.InputStream;
import java.security.Principal;

import com.whitebearsolutions.caldav.CalDAVException;
import com.whitebearsolutions.caldav.security.acl.CalDAVResourceACL;
import com.whitebearsolutions.caldav.session.CalDAVTransaction;

/**
 * 
 * Interface for the Calendar store
 * 
 * Your implementation of the CalDAVStore must have a contructor
 * which takes a single parameter of type ServletConfig.
 * You can use the ServletConfig to retrieve any parameters required
 * to initialise your store.
 * 
 * 
 * @author Ricardo Lorenzo
 */

public interface CalDAVStore
{
	/**
	 * Indica que una nueva petici&oacute;n o transacci&oacute;n con este
	 * almac&eacute;n ha sido iniciada. La petici&oacute;n es terminada por los
	 * m&eacute;todos {@link #commit()} o {@link #rollback()}.
	 * 
	 * @param principal
	 *            El objecto <code>java.security.Principal</code> que inicia la
	 *            petici&oacute;n o <code>null</code> si este no est&aacute;
	 *            disponible.
	 * 
	 * @throws CalDAVException
	 */
	CalDAVTransaction begin(Principal principal);

	/**
	 * Verifica si la informaci&oacute;n de autenticaci&oacute;n es
	 * v&aacute;lida. En caso contrario lanza una excepci&oacute;n-
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 */
	void checkAuthentication(CalDAVTransaction transaction);

	/**
	 * Returns the collection of permissions for the resource through  
	 * <code>CalDAVResurceACL</code>.
	 * 
	 * @param transaction
	 *            Indicates that the method is called in the context of a transaction
	 * @param folder_uri
	 *            URI folder
	 */
	CalDAVResourceACL getResourceACL(CalDAVTransaction transaction, String resourceUri);

	/**
	 * get the root path of the object store.

	 * 
	 * @return absolute path of the root
	 */
	String getRootPath();

	/**
	 * Commit all changes.
	 * 
	 * @param transaction
	 *            The transaction to commit.
	 */
	void commit(CalDAVTransaction transaction);

	/**
	 * Rollback any changes maded during the transaction.
	 * 
	 * @param transaction
	 *            The transactio to rollback.
	 */
	void rollback(CalDAVTransaction transaction);

	/**
	 * Creates a folder based on the path passed via the 
	 * <code>folder_uri</code>.
	 * 
	 * @param transaction
	 *            transaction to do the work within.
	 *            
	 * @param folder_uri
	 *            The URI of the folder
	 */
	void createFolder(CalDAVTransaction transaction, String folder_uri);

	/**
	 * Check if the given resources exists in the folder.
	 * 
	 * @param transaction
	 *            Transaction the access is made within.
	 *            
	 * @param folder_uri
	 *            The URI of the folder
	 */
	boolean resourceExists(CalDAVTransaction transaction, String resourceUri);

	/**
	 * Crea un recurso de contenido en la posici&oacute;n especificada por
	 * <code>resource_uri</code>.
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param resource_uri
	 *            La URI del recurso de contenido
	 * @throws CalDAVException
	 */
	void createResource(CalDAVTransaction transaction, String resource_uri);

	/**
	 * Obtiene el contenido del recurso especificado por
	 * <code>resource_uri</code>.
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param resource_uri
	 *            La URI del recurso de contenido
	 * @return InputStream El flujo de datos donde se puede leer el recurso.
	 * @throws CalDAVException
	 */
	InputStream getResourceContent(CalDAVTransaction transaction, String resourceUri);

	/**
	 * Sets / stores the content of the resource specified by
	 * <code>resourceUri</code>.
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param resource_uri
	 *            La URI del recurso de contenido
	 * @param content
	 *            input stream de donde el contenido ser&aacute; leido
	 * @param contentType
	 *            tipo mime del recurso o <code>null</code> si es desconocido
	 * @param characterEncoding
	 *            codificaci&oacute;n de caracteres del recurso o
	 *            <code>null</code> si no se conoce
	 * @return tama&ntilde;o del recurso
	 * @throws CalDAVException
	 */
	long setResourceContent(CalDAVTransaction transaction, String resourceUri, InputStream content, String contentType,
			String characterEncoding);

	/**
	 * Obtiene los nombres de los hijos de la carpeta especificada por
	 * <code>folder_uri</code>.
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param folder_uri
	 *            La URI de la carpeta
	 * @return una (posiblemente vacia) lista de hijos, o <code>null</code> si
	 *         la URI apunta a un fichero
	 * @throws CalDAVException
	 */
	String[] getChildrenNames(CalDAVTransaction transaction, String folder_uri);

	/**
	 * Obtiene los nombres de los hijos de la carpeta especificada, incluyendo
	 * los objetos ocultos o de sistema. por <code>folder_uri</code>.
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param folder_uri
	 *            La URI de la carpeta
	 * @return una (posiblemente vacia) lista de hijos, o <code>null</code> si
	 *         la URI apunta a un fichero
	 * @throws CalDAVException
	 */
	String[] getAllChildrenNames(CalDAVTransaction transaction, String folder_uri);

	/**
	 * Obtiene el tama&ntilde;o del contenido del recurso especificado por
	 * <code>resource_uri</code>.
	 * 
	 * @param transaction
	 *            indica que el metodo se encuentra en el contexto de una
	 *            transacci&oacute;n CalDAV
	 * @param path
	 *            La URI del contenido del recurso
	 * @return tama&ntilde;o en bytes, <code>-1</code> declara este valor como
	 *         invalido e intenta definirlo a trav&eacute;s de las propiedades
	 *         si es posible.
	 * @throws CalDAVException
	 */
	long getResourceLength(CalDAVTransaction transaction, String path);

	/**
	 * Borra el objeto especificado por <code>uri</code>.
	 * 
	 * @param transaction
	 *            indica al m&eacute;todo la transacci&oacute;n o contexto
	 *            CalDAV *
	 * @param uri
	 *            URI del objeto. content resource or folder
	 * @throws WebdavException
	 *             if something goes wrong on the store level
	 */
	void removeObject(CalDAVTransaction transaction, String uri);

	/**
	 * Gets the storedObject specified by <code>uri</code>
	 * 
	 * @param transaction
	 *            indicates that the method is within the scope of a WebDAV
	 *            transaction
	 * @param uri
	 *            URI
	 * @return StoredObject
	 */
	StoredObject getStoredObject(CalDAVTransaction transaction, String uri);
}
