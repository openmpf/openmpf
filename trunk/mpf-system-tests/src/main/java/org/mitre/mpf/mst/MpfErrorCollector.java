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

package org.mitre.mpf.mst;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.rules.ErrorCollector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class MpfErrorCollector extends ErrorCollector {

	private final List<Throwable> _errors = new ArrayList<>();

	/**
	 * This is overridden so that Jenkins doesn't treat each failed assertion as a separate failed test.
	 */
	@Override
	protected void verify() throws Throwable {
		if (_errors.isEmpty()) {
			return;
		}
		if (_errors.size() == 1) {
			throw _errors.get(0);
		}
		Assert.fail(combineErrorMessages());
	}

	private String combineErrorMessages() {
		StringWriter stringWriter = new StringWriter();
		try (PrintWriter errorMsgWriter = new PrintWriter(stringWriter)) {
			errorMsgWriter.printf("There were %s errors:\n", _errors.size());
			for (Throwable error : _errors) {
				error.printStackTrace(errorMsgWriter);
				errorMsgWriter.println("------");
			}
		}
		return stringWriter.toString();
	}

	@Override
	public void addError(Throwable error) {
		_errors.add(error);
	}

	public <T> void checkNowThat(T value, Matcher<T> matcher) {
		checkNowThat("", value, matcher);
	}

	public <T> void checkNowThat(String reason, T value, Matcher<T> matcher) {
		try {
			MatcherAssert.assertThat(reason, value, matcher);
		}
		catch (AssertionError error) {
			if (_errors.isEmpty()) {
				throw error;
			}
			_errors.add(error);
			Assert.fail(combineErrorMessages());
		}
	}
}
