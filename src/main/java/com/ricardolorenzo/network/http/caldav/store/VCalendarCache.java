/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ricardolorenzo.network.http.caldav.store;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ricardolorenzo.icalendar.StorePath;
import com.ricardolorenzo.icalendar.StorePathException;
import com.ricardolorenzo.icalendar.StorePathLock;
import com.ricardolorenzo.icalendar.StorePathLockException;
import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class VCalendarCache {
	// prefixed with a '.' so it it won't appear in listings for PROPFIND
	private static final String CALENDAR_CACHE = ".calendar_cache";
	private final static Logger logger = LoggerFactory.getLogger(VCalendarCache.class);
    private VCalendar vcalendar;

    public VCalendarCache(CalDAVStore store, StorePath vcalendar_file) throws Exception {
        this.vcalendar = getVCalendar(store, vcalendar_file);
    }

    public VCalendar getVCalendar() {
        return this.vcalendar;
    }

    public static VCalendar getVCalendar(CalDAVStore store, StorePath _f) throws VCalendarException, StorePathLockException, StorePathException {
        if (_f == null || !_f.exists()) {
            return new VCalendar();
        }

        StorePath cache_file;
        StorePath directory = _f.getParentFile();
        if (directory == null) {
            cache_file = new FileSystemStorePath(store, CALENDAR_CACHE);
        } else {
            cache_file = new FileSystemStorePath(store, directory.getAbsolutePath(), CALENDAR_CACHE);
        }

        if (!cache_file.exists()) {
            VCalendar _vc = new VCalendar( _f);
            serializeVCalendar(_vc, cache_file);
            return _vc;
        }

        return deSerializeVCalendar(cache_file);
    }

    public static void putVCalendar(CalDAVStore store, VCalendar _vcalendar, StorePath vcalendar_file) throws VCalendarException, StorePathLockException
             {
        if (vcalendar_file == null) {
            throw new VCalendarException("invalid VCalendar file");
        }

        StorePath cache_file;
        StorePath directory = vcalendar_file.getParentFile();
        if (directory == null) {
            cache_file = new FileSystemStorePath(store, CALENDAR_CACHE);
        } else {
            cache_file = new FileSystemStorePath(store, directory.getAbsolutePath(), CALENDAR_CACHE);
        }

        serializeVCalendar(_vcalendar, cache_file);
    }

    private static void serializeVCalendar(VCalendar _vcalendar, StorePath _file) throws StorePathLockException {
    	
    	StorePathLock _fl =_file.lock();
        ObjectOutputStream os;
		try
		{
			os = new ObjectOutputStream(new FileOutputStream(((FileSystemStorePath)_file).getFilePath()));
		     _fl.lock();
		        try {
		            os.writeObject(_vcalendar);
		            os.flush();
		        } finally {
		            _fl.unlock();
		            os.close();
		        }
		 
		}
		catch (FileNotFoundException e)
		{
			throw new StorePathLockException(e.getMessage(), e);
		}
		catch (IOException e)
		{
			throw new StorePathLockException(e.getMessage(), e);
		}
      }

    private static VCalendar deSerializeVCalendar(StorePath _file) throws StorePathException  {
        ObjectInputStream is;
		try
		{
			is = new ObjectInputStream(new FileInputStream(((FileSystemStorePath)_file).getFilePath()));
	        try {
	            return (VCalendar) is.readObject();
	        } catch (ClassNotFoundException e) {
	        	logger.error("deserialize", e);
	            throw new IOException("cannot read VCalendar object from cache file [" + _file.getAbsolutePath() + "]");
	        } finally {
	            is.close();
	        }
		}
		catch (FileNotFoundException e1)
		{
			throw new StorePathException(e1.getMessage(), e1);
		}
		catch (IOException e1)
		{
			throw new StorePathException(e1.getMessage(), e1);
		}
    }
}
