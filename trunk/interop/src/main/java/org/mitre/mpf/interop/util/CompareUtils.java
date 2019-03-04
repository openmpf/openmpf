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


package org.mitre.mpf.interop.util;

import java.util.*;

public class CompareUtils {

	private static final Comparator<Map.Entry<String, String>> MAP_ENTRY_COMPARATOR =
			Map.Entry.<String, String>comparingByKey()
					.thenComparing(Map.Entry.comparingByValue());


	public static final Comparator<Map<String, String>> MAP_COMPARATOR = Comparator
			.nullsFirst(Comparator
				.<Map<String, String>>comparingInt(Map::size)
				.thenComparing((m1, m2) -> {
				    if (m1 == m2) {
				    	return 0;
				    }
					Set<Map.Entry<String, String>> entrySet1 = new TreeSet<>(MAP_ENTRY_COMPARATOR);
				    entrySet1.addAll(m1.entrySet());

					Set<Map.Entry<String, String>> entrySet2 = new TreeSet<>(MAP_ENTRY_COMPARATOR);
					entrySet2.addAll(m2.entrySet());

					Iterator<Map.Entry<String, String>> iter1 = entrySet1.iterator();
					Iterator<Map.Entry<String, String>> iter2 = entrySet2.iterator();
					while (iter1.hasNext()) {
						int entryCompare = MAP_ENTRY_COMPARATOR.compare(iter1.next(), iter2.next());
						if (entryCompare != 0) {
							return entryCompare;
						}
					}
					return 0;
				}));



	public static <T extends Comparable<T>> Comparator<T> nullsFirst() {
		return Comparator.nullsFirst(Comparator.naturalOrder());
	}



	private CompareUtils() {
	}
}
