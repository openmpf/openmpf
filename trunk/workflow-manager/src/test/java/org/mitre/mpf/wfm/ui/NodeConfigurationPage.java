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

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeConfigurationPage {
	private static final Logger log = LoggerFactory.getLogger(NodeConfigurationPage.class);
	public static String PAGE_TITLE = "Workflow Manager Web App";
	public static String PAGE_URL = "workflow-manager/#/adminNodeConfiguration";
	public static String NAV_ID = "menu_adminNodeConfiguration";


	public NodeConfigurationPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page is: "
							+ driver.getCurrentUrl());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		log.debug("Current Title:" + driver.getTitle() + "  Desired:"
				+ PAGE_TITLE);
		return driver.getTitle().startsWith(PAGE_TITLE)
				&& driver.getCurrentUrl().contains(PAGE_URL)
				&& Utils.checkIDExists(driver, "catalog-area");
	}


	public static NodeConfigurationPage getNodeConfigurationPage(WebDriver driver) {
		// must be on homepage
		if (!HomePage.ValidPage(driver)) {
			throw new IllegalStateException("This is not Home Page of logged in user, current page is: " + driver.getCurrentUrl());
		}
		// click the nav link
		Utils.safeClickById(driver, NodeConfigurationPage.NAV_ID);

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return NodeConfigurationPage.ValidPage(d);
			}
		});

		return new NodeConfigurationPage(driver);
	}

	public int getCurrentServicesCount(WebDriver driver) {
		if (!HomePage.ValidPage(driver)) {	throw new IllegalStateException("This is not Home Page of logged in user, current page is: " + driver.getCurrentUrl());	}
		
		//get list of services
		String classname = "service-item-cart";//span
		log.info("Finding list of elements with class="+classname);
		List<WebElement> carts = driver.findElements(By.className(classname));//grab the carts
		log.info("#carts:"+carts.size());
		if(carts.size() == 0) return -1;
		WebElement cart = carts.get(0);
		List<WebElement> serviceItems = cart.findElements(By.className("dnd-item-count-input"));
		log.info("#service-items:" + serviceItems.size());
		int count = 0;
		for (WebElement row : serviceItems) {
			String ele = row.getAttribute("value");
			//log.info(ele);
			count += Integer.parseInt(ele);
		}

		return count;
	}

//	TODO: Use me!
//	public boolean addService(WebDriver driver) {
//		// must be on homepage
//		if (!HomePage.ValidPage(driver)) {	throw new IllegalStateException("This is not Home Page of logged in user, current page is: " + driver.getCurrentUrl());	}
//		//dragNdrop
//		WebElement sourceElement = driver.findElement(By.className("this-does-not-exist")); //TODO: update this class name!
//		WebElement destinationElement = driver.findElement(By.className("ui-droppable"));
//		log.info("drag from source:"+sourceElement.getText());		
//		log.info("drop to destination:{} {} {}", destinationElement.getAttribute("id"), destinationElement.getLocation().getX(), destinationElement.getLocation().getY());
//
//		// move mouse to top-left corner of source element then drag to top-left corner of destination element and release
//		// NOTE: this will not work if the Xvfb window size is too small to show the destination element
//		// NOTE: problems occur in Xvfb if you try to drag an element to a location that requires the window pane to scroll
//		new Actions(driver).moveToElement(sourceElement, 0, 0).clickAndHold().moveToElement(destinationElement, 0, 0).release().perform();
//		
//		log.info("DragNDrop complete");
//		
//		log.info("saving configuration - open dialog");
//		Utils.safeClickById(driver, "saveConfigButton");		
//		// Wait for the dialog to load
//		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
//			public Boolean apply(WebDriver d) {
//				return Utils.checkClassExists(d, "save_config");
//			}
//		});
//		log.info("saving configuration");
//	     Utils.safeClickByCss(driver, ".save_config");
//	     log.info("Wait for the dialog to close");
//		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
//			public Boolean apply(WebDriver d) {
//				return !Utils.checkClassExists(d, "modal-dialog");
//			}
//		});
//		if(!Utils.checkClassExists(driver, "modal-dialog")){
//			log.info("Dialog Closed");
//		}else{
//			log.info("Dialog Not Closed");
//		}
//		return true;
//	}
	
}
