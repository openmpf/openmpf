/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data;

import org.hibernate.dialect.PostgreSQL94Dialect;
import org.springframework.core.annotation.AnnotationUtils;

import javax.persistence.Column;
import java.sql.Types;

public class EnhancedPostgreSQLDialect extends PostgreSQL94Dialect {

   public EnhancedPostgreSQLDialect() {
       doNotSetStringColumnLengthWhenDefaultValueIsUsed();
       // Change timestamps to include timezone.
       registerColumnType(Types.TIMESTAMP, "timestamp with time zone");
   }


    /**
     * When using the {@link Column} without specifying a length, the value of {@link Column#length()} is used for
     * the maximum length for that column. When no length is specified, then we will typically want that column
     * to not have a maximum size.
     */
   private void doNotSetStringColumnLengthWhenDefaultValueIsUsed() {
       // Currently defaultLength = 255
       int defaultLength = (int) AnnotationUtils.getDefaultValue(Column.class, "length");

       // This handles the case where @Column.length is explicitly set to some value below defaultLength.
       // Since the value was explicitly set, we should use that value.
       registerColumnType(Types.VARCHAR, defaultLength - 1, "varchar($l)");

       // This handles the case where either no length is provided or (unfortunately) when the length is explicitly
       // set to the default length. To explicitly set the column length to the default,
       // `@Column(columnDefinition = "VARCHAR(255)")` can be used.
       // The length provided to registerColumnType specifies the maximum length, so if the explicitly set length is
       // greater than the default, the maximum length will be correctly set.
       registerColumnType(Types.VARCHAR, defaultLength, "text");
   }
}
