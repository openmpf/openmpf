/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
import java.util.function.Function;

public class CompareUtils {

    private static final Comparator<Map.Entry<String, String>> MAP_ENTRY_COMPARATOR =
            Map.Entry.<String, String>comparingByKey()
                    .thenComparing(Map.Entry.comparingByValue());


    public static final Comparator<Map<String, String>> MAP_COMPARATOR = Comparator
            .nullsFirst(Comparator
                .<Map<String, String>>comparingInt(Map::size)
                .thenComparing((m1, m2) -> {
                    //noinspection ObjectEquality - False positive
                    if (m1 == m2) {
                        return 0;
                    }
                    SortedSet<Map.Entry<String, String>> entrySet1 = new TreeSet<>(MAP_ENTRY_COMPARATOR);
                    entrySet1.addAll(m1.entrySet());

                    SortedSet<Map.Entry<String, String>> entrySet2 = new TreeSet<>(MAP_ENTRY_COMPARATOR);
                    entrySet2.addAll(m2.entrySet());

                    return doSortedSetCompare(entrySet1, entrySet2);
                }));



    public static <T extends Comparable<T>> Comparator<T> nullsFirst() {
        return Comparator.nullsFirst(Comparator.naturalOrder());
    }

    public static Comparator<String> stringCompare() {
        return Comparator.nullsFirst(
                String.CASE_INSENSITIVE_ORDER
                    .thenComparing(Comparator.naturalOrder()));
    }


    public static <T> Comparator<T> stringCompare(Function<T, String> toStringFunc) {
        return Comparator.comparing(toStringFunc, stringCompare());
    }


    public static <T, U> Comparator<T> sortedSetCompare(Function<T, SortedSet<U>> toSortedSetFunc) {
        return Comparator.nullsFirst(Comparator.comparing(toSortedSetFunc, CompareUtils::doSortedSetCompare));
    }


    private static <T> int doSortedSetCompare(SortedSet<T> s1, SortedSet<T> s2) {
        //noinspection ObjectEquality - False positive
        if (s1 == s2) {
            return 0;
        }

        Comparator<? super T> comparator = s1.comparator();
        if (comparator == null) {
            comparator = (a, b) -> ((Comparable<T>) a).compareTo(b) ;
        }

        Iterator<T> iter1 = s1.iterator();
        Iterator<T> iter2 = s2.iterator();

        while (true) {
            boolean hasNext1 = iter1.hasNext();
            boolean hasNext2 = iter2.hasNext();
            if (!hasNext1 && !hasNext2) {
                return 0;
            }

            T item1 = hasNext1 ? iter1.next() : null;
            T item2 = hasNext2 ? iter2.next() : null;
            if (item1 == null && item2 == null) {
                continue;
            }

            if (item1 == null) {
                return -1;
            }
            if (item2 == null) {
                return 1;
            }

            int itemCompare = comparator.compare(item1, item2);
            if (itemCompare != 0) {
                return itemCompare;
            }
        }
    }



    private CompareUtils() {
    }
}
