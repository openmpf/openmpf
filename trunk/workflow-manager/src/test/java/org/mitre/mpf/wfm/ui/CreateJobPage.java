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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mitre.mpf.wfm.ui.Utils.safeSelectUiSelectByIndex;
import static org.mitre.mpf.wfm.ui.Utils.safeSelectUiSelectByText;

public class CreateJobPage {
	private static final Logger log = LoggerFactory.getLogger(CreateJobPage.class);
	public static String PAGE_TITLE = "Workflow Manager Web App";
	public static String PAGE_URL = "workflow-manager/#/server_media"; 
	public static String NAV_ID = "menu_server_media";

	public CreateJobPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page is: "
							+ driver.getCurrentUrl());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		log.debug("Current Title:" + driver.getTitle() + "  Desired:"
				+ PAGE_TITLE);
		return driver.getTitle().startsWith(PAGE_TITLE) && driver.getCurrentUrl().contains(PAGE_URL) && Utils.checkIDExists(driver, "jobPipelineSelectServer");
	}
	
	public static CreateJobPage getCreateJobPage(WebDriver driver) {
		//must be on homepage
		if (!HomePage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page"
							+ "is: " + driver.getCurrentUrl());
		}
		// click the nav link
		Utils.safeClickById(driver,CreateJobPage.NAV_ID);

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return CreateJobPage.ValidPage(d);
			}
		});

		return new CreateJobPage(driver);
	}

	/*
	public boolean mediaExists(WebDriver driver,String base_url) throws InterruptedException {
		log.info("[CreateJobPage] mediaExists");
		if (!CreateJobPage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not the correct page, current page"
							+ "is: " + driver.getCurrentUrl() + " should be " + this.PAGE_URL);
		}

		//switch to uploads
		Utils.safeClickById(driver, "btn-display-upload-root"); //media will not be checked
		Thread.sleep(2000);//wait for it to populate
		log.info("View Uploads Only tab click");

		//click the last uploaded file
		List<WebElement> rows = driver.findElements(By.xpath("//table[@id='file_list_server']/tbody/tr"));
		log.info("#Files:" + rows.size());
		return (rows.size() > 1);
	}*/

	/**
	 * Run a job on the server media
	 * @param driver
	 * @param base_url
	 * @return
	 * @throws InterruptedException 
	 */
	public JobStatusPage createJobFromUploadedMedia(WebDriver driver,String base_url,String filename,String pipeline_name, int priority,int num_rows) throws InterruptedException{
		log.info("[CreateJobPage] createJobFromUploadedMedia");
		if (!CreateJobPage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not the correct page, current page"
							+ "is: " + driver.getCurrentUrl() +" should be " +this.PAGE_URL);
		}
		
		//switch to uploads
		//Utils.safeClickById(driver, "btn-display-upload-root"); //media will not be checked
		//Thread.sleep(2000);//wait for it to populate
		//log.info("View Uploads Only tab click");

		//click on the remote-media directory and wait for it
		log.info("clicking remote-media");
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
		// wait for files to load
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return d.findElement(By.id("file_list_server")) != null;
			}//may need to check for li
		});

		/*
		log.info("finding the first uploaded file");
		rows = driver.findElements(By.xpath("//table[@id='file_list_server']/tbody/tr"));
		idx = rows.size();
		if(rows.size() <= 1 && idx < num_rows){
			log.error("[createJobFromUploadedMedia] No Files in the media");
			throw new IllegalStateException("[createJobFromUploadedMedia] No Files in the media " + driver.getCurrentUrl());
		}
		log.info("#Files Available:" + idx);
		*/
		log.info("finding the file: "+filename);
		WebElement search = driver.findElement(By.xpath("//div[@id='file_list_server_filter']/label/input"));
		search.sendKeys(filename);//search
		Thread.sleep(2000);


		//select the first file
		log.info("clicking the uploaded file:"+filename);
		driver.findElement(By.xpath("//table[@id='file_list_server']/tbody/tr[1]/td[1]/input")).click();



		//select the desired pipeline from the select list
		found = safeSelectUiSelectByText( driver, "jobPipelineSelectServer", pipeline_name );
		log.info("Pipeline found:"+found);

		Thread.sleep(1000);

		//select the desired priority
		log.info("clicking the priority");
		found = safeSelectUiSelectByIndex( driver, "jobPrioritySelectServer", priority );
		log.info("Priority found:"+found);

	    //quick sleep to make sure the angular controller process all events
	    Thread.sleep(1000);

		log.info("submit job ");
		Utils.safeClickById(driver, "btn-submit-checked"); //submit media

		log.info("Wait 10 sec for job status");
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return JobStatusPage.ValidPage(d);
			}
		});
		log.info("on job status page");
		return new JobStatusPage(driver);
	}

}
