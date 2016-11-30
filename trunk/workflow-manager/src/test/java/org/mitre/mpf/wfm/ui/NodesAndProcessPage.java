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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodesAndProcessPage {
	private static final Logger log = LoggerFactory
			.getLogger(NodesAndProcessPage.class);
	public static String PAGE_TITLE = "Workflow Manager Web App";
	public static String PAGE_URL = "workflow-manager/#/adminNodesAndProcesses";
	public static String NAV_ID = "menu_adminNodesAndProcesses";

	public static String NODEMANAGER_URL = "8008";

	public NodesAndProcessPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page is: "
							+ driver.getCurrentUrl());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		boolean exists = Utils.checkIDExists(driver, "nodes-and-processes-table");
		log.info("Current Title: {} Desired:{} Current Url: {}, table exists: {}", driver.getTitle(),PAGE_TITLE,driver.getCurrentUrl(),exists);
		return driver.getCurrentUrl().contains(PAGE_URL)
				&& exists;
	}

	public static boolean ValidNodeManagerPage(WebDriver driver) {
		log.info("Current Title:" + driver.getTitle());
		List<WebElement> h2s = driver.findElements(By.xpath("//h2"));
		boolean valid = false;
		for (WebElement ele : h2s) {
			log.info("h2:" + ele.getText());
			if (ele.getText().startsWith("Cluster participants as of")) {
				valid = true;
				break;
			}
		}
		return valid;
	}

	public static NodesAndProcessPage getNodesAndProcessPage(WebDriver driver) {
		log.info("Going to NodesAndProcessPage");
		// must be on homepage
		if (!HomePage.ValidPage(driver)) {
			throw new IllegalStateException("This is not Home Page of logged in user, current page is: "+ driver.getCurrentUrl());
		}
		log.info("Clicking link : "+ NodesAndProcessPage.NAV_ID);
		// click the nav link
		Utils.safeClickById(driver, NodesAndProcessPage.NAV_ID);

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 20)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return NodesAndProcessPage.ValidPage(d);
			}
		});

		return new NodesAndProcessPage(driver);
	}

	public List<String> getCurrentNodesAndProcess(WebDriver driver) {
		if (!HomePage.ValidPage(driver)) {	throw new IllegalStateException("This is not Home Page of logged in user, current page is: " + driver.getCurrentUrl());	}
		
		// get list of shutdown btns
		List<WebElement> rows = driver.findElements(By
				.xpath("//*[@id='dataTable-processes']/tbody/tr"));
		ArrayList<String> list = new ArrayList<String>();

		for (WebElement row : rows) {
			List<WebElement> columns = row.findElements(By.tagName("td"));
			list.add(columns.get(0).getText() + ":" + columns.get(2).getText());// name:state
		}

		return list;
	}
	
	public boolean stopNode(WebDriver driver,final String node_name){
		//get all the stop buttons
		List<WebElement> btns = Utils.getClassValues(driver, "anp_shutdownbtn");
		//find our button
		for(WebElement ele : btns){
			if(ele.getAttribute("value").equals(node_name)){
				log.info("Shutting down node: "+node_name);
				ele.click();
				// Wait for the status to change, timeout after 10 seconds
				(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
					public Boolean apply(WebDriver d) {
						for (int attempts = 0; attempts < 10; attempts++) { // work around auto-refresh issues
							try {
								List<WebElement> rows = d.findElements(By.xpath("//*[@id='dataTable-processes']/tbody/tr"));
								for (WebElement row : rows) {
									List<WebElement> columns = row.findElements(By.tagName("td"));
									if (columns.get(0).getText().endsWith(node_name)) {
										return columns.get(2).getText().equals("Stopped");
									}
								}
							} catch (StaleElementReferenceException e) {
								// nothing
							}
						}
						return false;
					}
				});
				return true;
			}
		}
		return false;
	}
	public boolean startNode(WebDriver driver,final String node_name){
		//get all the stop buttons
		List<WebElement> btns = Utils.getClassValues(driver, "anp_startbtn");
		//find our button
		for(WebElement ele : btns){
			if(ele.getAttribute("value").equals(node_name)){
				log.info("Starting up node: "+node_name);
				ele.click();
				// Wait for the status to change, timeout after 10 seconds
				(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
					public Boolean apply(WebDriver d) {
						for (int attempts = 0; attempts < 10; attempts++) { // work around auto-refresh issues
							try {
								List<WebElement> rows = d.findElements(By.xpath("//*[@id='dataTable-processes']/tbody/tr"));
								for (WebElement row : rows) {
									List<WebElement> columns = row.findElements(By.tagName("td"));
									if (columns.get(0).getText().endsWith(node_name)) {
										return columns.get(2).getText().equals("Running");
									}
								}
							} catch (StaleElementReferenceException e) {
								// nothing
							}
						}
						return false;
					}
				});
				return true;
			}
		}
		return false;
	}

	public List<String> getCurrentNodesAndProcessFromNodeManager(WebDriver driver, String node_mgr_url) {
		log.info("Checking node manager at {}",node_mgr_url);
		// load the node manager page
		driver.get(node_mgr_url);
		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return NodesAndProcessPage.ValidNodeManagerPage(d);
			}
		});
		log.info("Looking for element meta");
		WebElement meta = driver.findElement(By.xpath("//meta"));
		// meta
		List<WebElement> rows = driver.findElements(By.xpath("//body/table[1]/tbody/tr"));// first table

		ArrayList<String> list = new ArrayList<String>();
		log.info("Table found with [" + rows.size() + "] rows. Building list. ");
		for (int rnum = 1; rnum < rows.size(); rnum++) {// skip first (0) since
														// its the headers
			List<WebElement> columns = rows.get(rnum).findElements(	By.tagName("td"));
			list.add(columns.get(0).getText()+ ":" + columns.get(1).getText() + ":" + columns.get(2).getText());// name:rank:state
		}
		return list;
	}

}
