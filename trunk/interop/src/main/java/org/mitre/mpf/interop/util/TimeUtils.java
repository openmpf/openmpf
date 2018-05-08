/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.interop.util;

import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class TimeUtils {

    // All timestamps in OpenMPF should adhere to this date/time pattern.
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.S";

    // The timestampFormatter must remain as a static, or the jackson conversion to JSON will no longer work
    private static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    /**
     * Parse the timestamp String into a LocalDateTime using the date/time pattern adhered to by OpenMPF.
     * @param timestampStr timestamp String, may not be null
     * @return timestamp as a LocalDateTime
     * @throws MpfInteropUsageException is thrown if the timestamp String is null. Throws DateTimeParseException if the timestamp String isn't parsable
     * using the date/time pattern adhered to by OpenMPF.
     */
    public static LocalDateTime parseStringAsLocalDateTime(String timestampStr) throws MpfInteropUsageException, DateTimeParseException {
        if ( timestampStr == null ) {
            throw new MpfInteropUsageException("Error, timestamp String may not be null");
        } else {
            return timestampFormatter.parse(timestampStr, LocalDateTime::from);
        }
    }

    /**
     * Format the LocalDateTime as a timestamp String using the date/time pattern adhered to by OpenMPF.
     * @param timestamp timestamp as a LocalDateTime. May be null.
     * @return timestamp as a String, will be null if timestamp is null.
     */
    public static String getLocalDateTimeAsString(LocalDateTime timestamp) {
        if ( timestamp == null ) {
            return null;
        } else {
            return timestampFormatter.format(timestamp);
        }
    }

    /**
     * Parse the timestamp String into a Date using the date/time pattern adhered to by OpenMPF.
     * @param timestamp timestamp String, may not be null
     * @return timestamp as a Date
     * @throws MpfInteropUsageException is thrown if the timestamp String is null. Throws DateTimeParseException if the timestamp String isn't parsable
     * using the date/time pattern adhered to by OpenMPF.
     */
    public static Date parseStringAsDate(String timestamp) throws DateTimeParseException, MpfInteropUsageException {
        if ( timestamp == null ) {
            throw new MpfInteropUsageException("Error, timestamp may not be null.");
        } else {
            return Date.from(parseStringAsLocalDateTime(timestamp).atZone(ZoneId.systemDefault()).toInstant());
        }
    }

    /**
     * Format the Date as a timestamp String using the date/time pattern adhered to by OpenMPF.
     * @param date timestamp as a Date. May be null.
     * @return timestamp as a String in the system time zone, or null if timestamp is null.
     */
    public static String getDateAsString(Date date) {
        if ( date == null ) {
            return null;
        } else {
            LocalDateTime timestamp = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return timestampFormatter.format(timestamp);
        }
    }

    public static LocalDateTime millisToDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    public static String millisToDateTimeString(long milllis) {
        return getLocalDateTimeAsString(millisToDateTime(milllis));
    }
}
