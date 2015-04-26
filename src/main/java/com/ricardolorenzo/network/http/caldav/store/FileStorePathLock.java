package com.ricardolorenzo.network.http.caldav.store;

import com.ricardolorenzo.file.lock.FileLock;
import com.ricardolorenzo.file.lock.FileLockException;
import com.ricardolorenzo.icalendar.StorePath;
import com.ricardolorenzo.icalendar.StorePathLock;
import com.ricardolorenzo.icalendar.StorePathLockException;

public class FileStorePathLock implements StorePathLock
{

	private StorePath storePath;
	private FileLock fileLock;

	public FileStorePathLock(StorePath storePath)
	{
		this.storePath = storePath;
		this.fileLock = new FileLock(((FileSystemStorePath)this.storePath).getFilePath());
	}

	@Override
	public void lock() throws StorePathLockException 
	{
		try
		{
			fileLock.lock();
		}
		catch (FileLockException e)
		{
			throw new StorePathLockException(e.getMessage(), e);
		}
		
	}

	@Override
	public void unlock()
	{
		fileLock.unlockQuietly();
	}

}
