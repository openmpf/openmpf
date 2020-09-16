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
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomePage {
	private static final Logger log = LoggerFactory.getLogger(HomePage.class);
	protected static String PAGE_TITLE = "Workflow Manager Web App";

	public HomePage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException("This is not Home Page of logged in user, current page is: "+ driver.getCurrentUrl() + " Title:" +driver.getTitle());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		log.info("Current Title:" + driver.getTitle() + "  Desired:"+ PAGE_TITLE);
		return driver.getTitle().startsWith(PAGE_TITLE);
	}

	/**
	 * Login as valid user
	 *
	 * @return LoginPage object
	 */
	public LoginPage logout(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page"
							+ "is: " + driver.getCurrentUrl());
		}
				
	    try {
			//clear all popups if possible when trying to logout
	 		//while noty popups exist
			while(driver.findElement(By.className("noty_text")) != null) {
				//click the "Close All" button
				Utils.safeClickById(driver,"button-1");
				//sleep for a second to wait for noty to display others from the
				// queue since "Close All" now only closes what is visible
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					log.error("Error while sleeping for 1000ms after closing noty notifications");
				}
			}
	    } catch (NoSuchElementException e) {
	        //will be thrown when driver.findElement(By.className("noty_text")) can't find the element
	    	log.info("No noty notifications present during logout.");
	    }
		
		//Hopefully it is impossible for a noty notification to pop up between these operations

		// click the dropdown
		log.info("Click #user_dropdown");
		Utils.safeClickById(driver, "user_dropdown");
	
		// click the logout link
		log.info("Click #logout");
		Utils.safeClickById(driver, "logout");

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return d.getTitle().startsWith(LoginPage.PAGE_TITLE);
			}
		});

		return new LoginPage(driver);
	}
	
	
	
}
