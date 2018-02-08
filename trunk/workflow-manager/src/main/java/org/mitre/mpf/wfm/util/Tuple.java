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

package org.mitre.mpf.wfm.util;

import org.javasimon.aop.Monitored;

@Monitored
public class Tuple<A, B> {
    private Tuple(){}
    private A first;
    public A getFirst() { return first; }

    private B second;
    public B getSecond() { return second; }

    public Tuple(A first, B second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int hashCode() {
        int result = 31391;
        result = 37 * result + (first == null ? 0 : first.hashCode());
        result = 37 * result + (second == null ? 0 : second.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || !(obj instanceof Tuple)) {
            return false;
        } else if(this == obj) {
            return true;
        } else {
            Tuple casted = (Tuple)obj;
            return (first == null ? casted.first == null : first.equals(casted.first))
                    && (second == null ? casted.second == null : second.equals(casted.second));
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<first='%s', second='%s'>",
                this.getClass().getSimpleName(),
                first,
                second);
    }
}
