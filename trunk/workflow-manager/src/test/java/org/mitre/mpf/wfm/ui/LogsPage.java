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

package org.mitre.mpf.wfm.ui;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogsPage {
	private static final Logger log = LoggerFactory.getLogger(LogsPage.class);
	public static String PAGE_TITLE = "Workflow Manager Web App";
	public static String PAGE_URL = "workflow-manager/#/adminLogs"; 
	public static String NAV_ID = "menu_adminLogs";
	
	public static String NODEMANAGER_URL = "8008";
	
	public LogsPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not correct page of logged in user, current page is: "
							+ driver.getCurrentUrl());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		log.debug("Current Title:" + driver.getTitle() + "  Desired:"
				+ PAGE_TITLE);
		return driver.getTitle().startsWith(PAGE_TITLE) && driver.getCurrentUrl().contains(PAGE_URL) && Utils.checkIDExists(driver, "logButtonsRow");
	}
	
	public static LogsPage getLogsPage(WebDriver driver) {
		//must be on homepage
		if (!HomePage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not correct Home page, current page"
							+ "is: " + driver.getCurrentUrl());
		}
		// click the nav link
		Utils.safeClickById(driver,LogsPage.NAV_ID);

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return LogsPage.ValidPage(d);
			}
		});

		return new LogsPage(driver);
	}
	
}
