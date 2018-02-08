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

package org.mitre.mpf.wfm.event;

import org.mitre.mpf.wfm.util.TextUtils;

import java.util.UUID;

public abstract class NotificationConsumer<T> implements Comparable<NotificationConsumer> {
	private String id;
	public String getId() { return id; }

	public NotificationConsumer() {
		this.id = UUID.randomUUID().toString();
	}

	public int hashCode() {
		return id.hashCode();
	}

	public boolean equals(Object other) {
		if(other == null || !(other instanceof NotificationConsumer)) {
			return false;
		} else {
			return id.equals(((NotificationConsumer)other).id);
		}
	}

	@Override
	public int compareTo(NotificationConsumer other) {
		if(other == null) {
			return 1;
		} else {
			return TextUtils.nullSafeCompare(id, other.id);
		}
	}

	public abstract void onNotification(Object source, T notification);
}
