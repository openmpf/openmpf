/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.util;

import com.google.common.base.Joiner;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collector;

/** Utility methods for cleaning user input. */
public class TextUtils {

	public static String getTrackUuid(String sha256, int offset, int x, int y, int width, int height, String type) {
		return DigestUtils.sha256Hex(
				StringUtils.join(
						Arrays.asList(sha256, Integer.toString(offset), Integer.toString(x), Integer.toString(y), Integer.toString(width), Integer.toString(height), type), ":"));
	}

	/** Null-safe trim operation that returns the trimmed input or {@literal null} if 1) the input is null, or 2) no characters remain after trimming. */
	public static String trim(String input) {
		return StringUtils.trimToNull(input);
	}

	/** Null-safe trim operation that returns the trimmed input or {@literal ""} if 1) the input is null, or 2) no characters remain after trimming. */
	public static String trimToEmpty(String input) {
		return (input == null) ? "" : StringUtils.trimToEmpty(input);
	}

	/** Null-safe trim operation that returns the trimmed, uppercase input or {@literal null} if 1) the input is null, or 2) no characters remain after trimming. */
	public static String trimToNullAndUpper(String input) {
		return StringUtils.upperCase(StringUtils.trimToNull(input));
	}

	public static String trimAndUpper(String input) {
		return input != null
				? input.toUpperCase().trim()
				: null;
	}

	public static <R> R trimAndUpper(Collection<String> strings, Collector<String, ?, R> collector) {
        return strings.stream()
		        .filter(Objects::nonNull)
				.map(TextUtils::trimAndUpper)
				.collect(collector);
	}


	/** Null-safe trim operation that returns the trimmed, lowercase input or {@literal null} if 1) the input is null, or 2) no characters remain after trimming. */
	public static String trimAndLower(String input) {
		return StringUtils.lowerCase(StringUtils.trimToNull(input));
	}

	/** Converts a String,String map into a string of keys and values in the form key1=value1;key2=value2;key3=value3 **/
	public static String mapToStringValues(Map<String,String> map) {
		if (map == null) {
			return null;
		}
		return Joiner.on(";").withKeyValueSeparator("=").join(map);
	}

	/** Null-safe case-sensitive equality operation. */
	public static boolean nullSafeEquals(String a, String b) {
		if(a == null && b == null) {
			return true;
		} else if((a == null && b != null) || (a != null && b == null)) {
			return false;
		} else {
			return a.equals(b);
		}
	}

	/** Null-safe case-sensitive equality operation. */
	public static boolean nullSafeEqualsIgnoreCase(String a, String b) {
		if(a == null && b == null) {
			return true;
		} else if((a == null && b != null) || (a != null && b == null)) {
			return false;
		} else {
			return a.equalsIgnoreCase(b);
		}
	}

	/** Returns a value even if the parameter is null. */
	public static int nullSafeHashCode(String str) {
		return str == null ? 37 : str.hashCode();
	}

	/** Compares the two inputs, either or both of which may be null. */
	public static int nullSafeCompare(String a, String b) {
		if(a == null && b == null) {
			return 0;
		} else if(a == null) {
			return -1;
		} else if(b == null) {
			return 1;
		} else {
			return a.compareTo(b);
		}
	}
}
