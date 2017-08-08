/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc.model;

public class AuthenticationModel {
	private boolean authenticated = false;
	private boolean admin = false;
	private String userPrincipalName = null;
	private boolean firstLogin = false;
	
	public AuthenticationModel(boolean authenticated, boolean admin, 
			String userPrincipalName, boolean firstLogin) {
		this.authenticated = authenticated;
		this.admin = admin;
		this.userPrincipalName = userPrincipalName;
		this.firstLogin = firstLogin;
	}
	
	public boolean isAuthenticated() {
		return authenticated;
	}
	
	public boolean isAdmin() {
		return admin;
	}
	
	public String getUserPrincipalName() {
		return userPrincipalName;
	}

	public boolean canProceedAsAdmin()  {
		if ( this.getUserPrincipalName() != null
				&& this.isAuthenticated()
				&& this.isAdmin() )
		{
			return true;
		}
		return false;
	}

	public boolean isFirstLogin() {
		return firstLogin;
	}
}
