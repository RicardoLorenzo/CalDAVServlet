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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.ricardolorenzo.file.lock.FileLock;
import com.ricardolorenzo.file.lock.FileLockException;
import com.ricardolorenzo.icalendar.VCalendar;
import com.ricardolorenzo.icalendar.VCalendarException;

/**
 * 
 * @author Ricardo Lorenzo
 * 
 */
public class VCalendarCache {
    private VCalendar vcalendar;

    public VCalendarCache(File vcalendar_file) throws Exception {
        this.vcalendar = getVCalendar(vcalendar_file);
    }

    public VCalendar getVCalendar() {
        return this.vcalendar;
    }

    public static VCalendar getVCalendar(File vcalendar_file) throws VCalendarException, IOException, FileLockException {
        if (vcalendar_file == null || !vcalendar_file.exists()) {
            return new VCalendar();
        }

        File cache_file;
        File directory = vcalendar_file.getParentFile();
        if (directory == null) {
            cache_file = new File("calendar_cache");
        } else {
            cache_file = new File(directory.getAbsolutePath() + File.separator + "calendar_cache");
        }

        if (!cache_file.exists()) {
            VCalendar _vc = new VCalendar(vcalendar_file);
            serializeVCalendar(_vc, cache_file);
            return _vc;
        }

        return deSerializeVCalendar(cache_file);
    }

    public static void putVCalendar(VCalendar _vcalendar, File vcalendar_file) throws VCalendarException, IOException,
            FileLockException {
        if (vcalendar_file == null) {
            throw new VCalendarException("invalid VCalendar file");
        }

        File cache_file;
        File _directory = vcalendar_file.getParentFile();
        if (_directory == null) {
            cache_file = new File("calendar_cache");
        } else {
            cache_file = new File(_directory.getAbsolutePath() + File.separator + "calendar_cache");
        }

        serializeVCalendar(_vcalendar, cache_file);
    }

    private static void serializeVCalendar(VCalendar _vcalendar, File _file) throws IOException, FileLockException {
        FileLock _fl = new FileLock(_file);
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(_file));
        _fl.lock();
        try {
            os.writeObject(_vcalendar);
            os.flush();
        } finally {
            _fl.unlockQuietly();
            os.close();
        }
    }

    private static VCalendar deSerializeVCalendar(File _file) throws IOException {
        ObjectInputStream is = new ObjectInputStream(new FileInputStream(_file));
        try {
            return (VCalendar) is.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("cannot read VCalendar object from cache file [" + _file.getAbsolutePath() + "]");
        } finally {
            is.close();
        }
    }
}
