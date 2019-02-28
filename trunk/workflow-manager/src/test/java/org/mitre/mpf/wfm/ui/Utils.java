/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.enums.EnvVar;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Utils {
	private static final Logger log = LoggerFactory.getLogger(Utils.class);
	public static String IMG_URL = null;
	public static String IMG_NAME = null;
	public static String VIDEO_URL = null;
	public static String LONG_VIDEO_URL = null;


	//the tests are run here unless environment variable TOMCAT_BASE_URL is set
	public static String BASE_URL = "http://localhost:8080";
	static {
		String base_url = System.getenv(EnvVar.TOMCAT_BASE_URL);
		if(base_url != null && base_url.length() > 0){
			Utils.BASE_URL = base_url;
			log.info("TOMCAT_BASE_URL Environment Variable and BASE_URL: {}", Utils.BASE_URL);
		}else{
			log.info("TOMCAT_BASE_URL Environment Variable NOT SET and BASE_URL: {}", Utils.BASE_URL);
		}
		Utils.IMG_NAME = "blue-cybernetic-background.jpg";
		Utils.IMG_URL = Utils.BASE_URL+"/workflow-manager/resources/img/" + Utils.IMG_NAME;

	}
	
	
	public static void safeClickById(WebDriver driver, String elementId) {
		WebElement webElement = driver.findElement(By.id(elementId));
		if (webElement != null) {
			webElement.click();
		} else {
			// Using the TestNG API for logging
			throw new IllegalStateException("Element: " + elementId
					+ ", is not available on page - " + driver.getCurrentUrl());
		}
	}

	public static void safeClickByCss(WebDriver driver, String css) {
		WebElement webElement = driver.findElement(By.cssSelector(css));
		if (webElement != null) {
			webElement.click();
		} else {
			// Using the TestNG API for logging
			throw new IllegalStateException("cssSelector: " + css
					+ ", is not available on page - " + driver.getCurrentUrl());
		}
	}
	public static void safeClickByClassname(WebDriver driver, String classname) {
		WebElement webElement = driver.findElement(By.className(classname));
		if (webElement != null) {
			webElement.click();
		} else {
			// Using the TestNG API for logging
			throw new IllegalStateException("classname: " + classname
					+ ", is not available on page - " + driver.getCurrentUrl());
		}
	}

	public static void safeClickByLinkText(WebDriver driver, String linktext) {
		WebElement webElement = driver.findElement(By.linkText(linktext));
		if (webElement != null) {
			webElement.click();
		} else {
			// Using the TestNG API for logging
			throw new IllegalStateException("link text: " + linktext
					+ ", is not available on page - " + driver.getCurrentUrl());
		}
	}
	
	public static boolean checkIDExists(WebDriver driver,String tagid) {
		try{
			driver.findElement(By.id(tagid));
		}catch(NoSuchElementException nse){
			return false;
		}
		return true;
	}
	public static boolean checkClassExists(WebDriver driver,String classname) {
		try{
			driver.findElement(By.className(classname));
		}catch(NoSuchElementException nse){
			return false;
		}
		return true;
	}
	
	public static List<WebElement> getClassValues(WebDriver driver,String classname) {
		List<WebElement> elements;
		try{
			elements = driver.findElements(By.className(classname));
		}catch(NoSuchElementException nse){
			return null;
		}
		return elements;
	}

	/** selects (based on text) one item in a ui-select angular ui widget
	 * @param driver - WebDriver currently running
	 * @param id - the ID of the ui-select element (without the css '#')
	 * @param menuItemText - the text of the menu (case in-sensitive)
     * @return true iff the item was found and was selected by clicking
     */
	public static boolean safeSelectUiSelectByText(WebDriver driver, String id, String menuItemText) {
//		log.info("safeSelectUiSelectByText -> selecting "+ menuItemText );
		boolean success = false;
		WebElement menuSearchBox = driver.findElement( By.id( id ) );
		menuSearchBox.click();	// need to click to get menu of pipelines
		List<WebElement> menuItems = menuSearchBox.findElements(By.cssSelector(".ui-select-choices-row"));	// hard-coded by library, we don't specify this when we use ui-select
		List<WebElement> textElements = null;
		for ( WebElement item : menuItems ) {
			textElements = item.findElements(By.cssSelector(".ng-binding"));////*[@id="jobPipelineSelectServer"]/div[2]/div/div/div[3]/div/span
			String text = textElements.get(0).getText();
			if ( text.equalsIgnoreCase(menuItemText) ) {
				item.click();
				success = true;
				break;
			}
		}
//		log.info("  safeSelectUiSelectByText returning " + success );
		return success;
	}


	/** selects (based on index) one item in a ui-select angular ui widget
	 * @param driver - WebDriver currently running
	 * @param id - the ID of the ui-select element (without the css '#')
	 * @param index - the 0-based menuitem index to select
	 * @return true iff the item was found and was selected by clicking
	 */
	public static boolean safeSelectUiSelectByIndex(WebDriver driver, String id, int index) {
//		log.info("safeSelectUiSelectByIndex -> selecting "+ index );
		boolean success = false;
		WebElement menuSearchBox = driver.findElement( By.id( id ) );
		menuSearchBox.click();	// need to click to get menu
		List<WebElement> menuItems = menuSearchBox.findElements(By.cssSelector(".ui-select-choices-row"));	// hard-coded by library, we don't specify this when we use ui-select
		if ( menuItems.size() > index ) {
			menuItems.get(index).click();
			success = true;
		}
//		log.info("  safeSelectUiSelectByIndex returning " + success );
		return success;
	}
}
