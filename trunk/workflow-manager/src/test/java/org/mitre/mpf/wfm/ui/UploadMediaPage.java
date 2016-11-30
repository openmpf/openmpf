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

import java.util.List;

import static org.mitre.mpf.wfm.ui.Utils.safeSelectUiSelectByText;

public class UploadMediaPage {
	private static final Logger log = LoggerFactory.getLogger(UploadMediaPage.class);
	public static String PAGE_TITLE = "Workflow Manager Web App";
	public static String PAGE_URL = "workflow-manager/#/server_media";
	public static String NAV_ID = "menu_server_media";
	

	public UploadMediaPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page is: "+ driver.getCurrentUrl());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		log.debug("Current Title:" + driver.getTitle() + "  Desired:" + PAGE_TITLE);
		return driver.getTitle().startsWith(PAGE_TITLE) && driver.getCurrentUrl().contains(PAGE_URL) && Utils.checkIDExists(driver, "fileManager");
	}
	
	public static UploadMediaPage getUploadMediaPage(WebDriver driver) {
		//must be on homepage
		if (!HomePage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page"
							+ "is: " + driver.getCurrentUrl());
		}
		// click the nav link
		log.debug("Nav link click:"+UploadMediaPage.NAV_ID);
		Utils.safeClickById(driver,UploadMediaPage.NAV_ID);

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return UploadMediaPage.ValidPage(d);
			}
		});

		return new UploadMediaPage(driver);
	}
	
	/***
	 * 
	 * @param driver
	 * @param base_url
	 * @return
	 */
	public String uploadMediaFromUrl(WebDriver driver,String base_url,String media_url) throws InterruptedException {
		if (!UploadMediaPage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not the correct page, current page" + "is: " + driver.getCurrentUrl());
		}
		//click on the remote-media directory and wait for it
		List< WebElement > rows = driver.findElements(By.className("list-group-item"));
		int idx = rows.size();
		//log.info("#level directories:" + idx);
		boolean found=false;
		for (WebElement row : rows) {
			String ele = row.getText();
			//log.info(ele);
			if(ele.equals("remote-media")){
				row.click();
				found=true;
			}
		}
		if (!found) {
			// Using the TestNG API for logging
			throw new IllegalStateException("remote-media not found " + driver.getCurrentUrl());
		}
		log.info("Clicking btn-upload and wating for modal");
		Utils.safeClickByClassname (driver, "btn-upload");
		// wait for modal to popup
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return d.findElement(By.id("submitURLs")) != null;
			}
		});

		log.info("sending media :"+media_url);
		driver.findElement(By.id("URLsInput")).sendKeys(media_url);//insert a img url

		log.info("clicking submit");
		Utils.safeClickById(driver, "submitURLs");

		log.info("Waiting for file upload 600s {}",media_url);
		(new WebDriverWait(driver, 600)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return d.findElement(By.className("localName")).getText().contains("Uploaded: ");
			}
		});
		log.info("File uploaded");

		log.info("clicking close");
		Utils.safeClickById(driver, "cancelURLUpload");
		Thread.sleep(600);

		return media_url;
	}
	
	/***
	 * 
	 * @param driver
	 * @param mediaUrl
	 * @return
	 * @throws InterruptedException 
	 */
	/*
	public void createJobFromUrl(WebDriver driver, String mediaUrl) throws Exception {
		if (!UploadMediaPage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not the correct page, current page"
							+ "is: " + driver.getCurrentUrl());
		}
		
		//go to the URL tab and wait a second
		driver.findElement(By.id("URLUploadTab")).click();
		Thread.sleep(1000);
		
		//clear urls input and input the mediaUrl
	    driver.findElement(By.id("URLsInput")).clear();
	    driver.findElement(By.id("URLsInput")).sendKeys(mediaUrl);
	    //check add directly to job, select the pipeline, and click the submit button (submit the job with the url in this case)	
	    driver.findElement(By.id("chkboxDirectlyToJob")).click();

		//select the desired pipepline from the select list
		String pipeline_name = "OCV FACE DETECTION PIPELINE";
		boolean selectedPipeline = safeSelectUiSelectByText( driver, "jobPipelineSelectServer", pipeline_name );
		driver.findElement(By.id("submitURLs")).click();
	}*/
}
