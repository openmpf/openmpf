/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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


    public static <T extends Comparable<T>> Comparator<Optional<T>> optionalCompare() {
        return optionalCompare(Comparator.<T>naturalOrder());
    }

    public static <T> Comparator<Optional<T>> optionalCompare(Comparator<T> comparator) {
        return Comparator.comparing(o -> o.orElse(null), Comparator.nullsFirst(comparator));
    }


    public static <T, U> Comparator<T> sortedSetCompare(Function<T, SortedSet<U>> toSortedSetFunc) {
        return Comparator.nullsFirst(Comparator.comparing(toSortedSetFunc, CompareUtils::doSortedSetCompare));
    }


    private static <T> int doSortedSetCompare(SortedSet<T> s1, SortedSet<T> s2) {
        // s1 and s2 are expected to have the same comparator. Passing in sets with different
        // comparators is not supported.
        Comparator<? super T> comparator = s1.comparator();
        if (comparator == null) {
            comparator = (a, b) -> ((Comparable<T>) a).compareTo(b);
        }
        return doCollectionCompare(s1, s2, comparator);
    }


    public static <T, U extends Comparable<U>> Comparator<T> listCompare(Function<T, List<U>> toListFunc) {
        return Comparator.nullsFirst(Comparator.comparing(
                toListFunc,
                (c1, c2) -> doCollectionCompare(c1, c2, Comparator.naturalOrder())));
    }


    private static <T> int doCollectionCompare(
            Collection<T> c1, Collection<T> c2, Comparator<? super T> comparator) {
        if (c1 == c2) {
            return 0;
        }

        var iter1 = c1.iterator();
        var iter2 = c2.iterator();
        while (true)  {
            var hasNext1 = iter1.hasNext();
            var hasNext2 = iter2.hasNext();
            if (hasNext1 && hasNext2) {
                var item1 = iter1.next();
                var item2 = iter2.next();
                int itemCompare = comparator.compare(item1, item2);
                if (itemCompare != 0) {
                    return itemCompare;
                }
            }
            else if (hasNext1) {
                return 1;
            }
            else if (hasNext2) {
                return -1;
            }
            else {
                return 0;
            }
        }
    }


    private CompareUtils() {
    }
}
