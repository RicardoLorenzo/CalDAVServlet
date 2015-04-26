package com.ricardolorenzo.network.http.caldav.store;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import com.ricardolorenzo.file.io.FileUtils;
import com.ricardolorenzo.file.lock.FileLockException;
import com.ricardolorenzo.icalendar.StorePath;
import com.ricardolorenzo.icalendar.StorePathLock;

public class FileSystemStorePath implements StorePath
{
	private File path;
	private CalDAVStore _store;

	FileSystemStorePath(CalDAVStore store, String relativePath)
	{
		this._store = store;
		path = new File(this._store.getRootPath(), relativePath);
	}

	FileSystemStorePath(CalDAVStore store, String path, boolean absolutePath)
	{
		this._store = store;
		if (absolutePath)
			this.path = new File(path);
		else
			this.path = new File(this._store.getRootPath(), path);
	}

	public FileSystemStorePath(CalDAVStore store, String absolutePath, String child)
	{
		this._store = store;
		path = new File(absolutePath, child);
	}

	@Override
	public boolean exists()
	{
		return path.exists();
	}

	@Override
	public StorePath getParentFile()
	{
		return new FileSystemStorePath(this._store, path.getParent(), true);
	}

	@Override
	public Reader getReader() throws FileNotFoundException, IOException
	{
		final InputStream is = new BufferedInputStream(new ByteArrayInputStream(readBytes(new FileInputStream(path))));
		return new InputStreamReader(is);
	}

	private static final byte[] readBytes(final InputStream is) throws IOException
	{
		final byte[] buffer = new byte[2048];
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final BufferedInputStream bufferInput = new BufferedInputStream(is);
		try
		{
			for (int i = bufferInput.read(buffer); i >= 0; i = bufferInput.read(buffer))
			{
				baos.write(buffer, 0, i);
			}
		}
		finally
		{
			bufferInput.close();
		}
		return baos.toByteArray();
	}

	@Override
	public void write(String entity) throws IOException, FileLockException
	{
		FileUtils.writeFile(this.path, toString());
	}

	@Override
	public StorePathLock lock()
	{
		return new FileStorePathLock(this);
	}

	/**
	 * Returns the StorePath as a File path.
	 * 
	 * @return
	 */
	public File getFilePath()
	{
		return path;
	}

	@Override
	public String getAbsolutePath()
	{
		return path.getAbsolutePath();
	}

}
