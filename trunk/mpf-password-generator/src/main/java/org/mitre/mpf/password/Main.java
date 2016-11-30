/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

	public static void main(String[] args) {		
		int amountOfPasswords = 1;		
		
//		String password = "mpf123";
//		String password = "mpfadm";
		String password = null;
		if (args.length > 0) {
		    try {
		    	password = args[0];
		        if(args.length > 1) {
		        	amountOfPasswords = Integer.parseInt(args[1]);
		        }
		    } catch (NumberFormatException e) {
		        System.err.println("Argument" + args[1] + " must be an integer.");
		        System.exit(1);
		    }
		} else {
			System.err.println("Args: <password> <amountOfPasswords>");
		} 		
		
		int i = 0;
		while (password != null && i < amountOfPasswords) {									
			//strength set to 11
			BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
			String hashedPassword = passwordEncoder.encode(password);

			System.out.println(hashedPassword);
			i++;
		}
	}
}
