/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Main {

	public static final int DEFAULT_ENCODER_STRENGTH = 12;

	public static final String USAGE = "Args: <raw-password> [encoder-strength=" + DEFAULT_ENCODER_STRENGTH + "]";

	public static void handleUsageError(String error) {
		System.err.println(error);
		System.err.println(USAGE);
		System.exit(1);
	}

    public static void main(String[] args) {
		String rawPassword;
    	int strength = DEFAULT_ENCODER_STRENGTH;

        if (args.length == 0 || args[0].equals("-h") || args[0].equals("-help")|| args[0].equals("--help")) {
        	System.out.println(USAGE);
        	System.exit(0);
		}

		rawPassword = args[0];
		if (args.length > 1) {
			try {
				strength = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				handleUsageError("\"" + args[1] + "\" must be an integer.");
			}
			if (strength < 4 || strength > 31) {
				handleUsageError("\"" + args[1] + "\" must be in the range [4, 31].");
			}
		}

        // bcrypt has built-in salts:
        // https://stackoverflow.com/questions/6832445/how-can-bcrypt-have-built-in-salts?answertab=votes#tab-top
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(strength);
        System.out.println(passwordEncoder.encode(rawPassword));
    }
}
