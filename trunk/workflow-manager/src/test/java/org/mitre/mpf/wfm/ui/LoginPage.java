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

package org.mitre.mpf.wfm.ui;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginPage {
	private static final Logger log = LoggerFactory.getLogger(LoginPage.class);
	public static String PAGE_TITLE = "Login Page";
	
	public LoginPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not sign in page, current page is: "
							+ driver.getCurrentUrl() + " Title:"+driver.getTitle());
		}
	}

	public static boolean ValidPage(WebDriver driver){
		log.info("Current Title:" + driver.getTitle() + "  Desired:"+ PAGE_TITLE);
		return driver.getTitle().equals(PAGE_TITLE);
	}
	
	/**
	 * Login as valid user
	 *
	 * @param userName
	 * @param password
	 * @return HomePage object
	 */
	public HomePage loginValidUser(WebDriver driver, String userName,String password) {

		WebElement element = driver.findElement(By.name("username"));
		element.sendKeys(userName);
		element = driver.findElement(By.name("password"));
		element.sendKeys(password);
		// Now submit the form.
		element.submit();

		// Wait for the page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {				
				return d.getTitle().startsWith(HomePage.PAGE_TITLE);
			}
		});

		return new HomePage(driver);
	}

}
