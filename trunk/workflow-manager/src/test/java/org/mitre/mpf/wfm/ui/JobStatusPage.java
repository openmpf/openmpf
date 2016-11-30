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

import java.util.List;

import java.util.List;

import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobStatusPage {
	private static final Logger log = LoggerFactory.getLogger(JobStatusPage.class);
	public static String PAGE_TITLE = "Workflow Manager Web App";
	public static String PAGE_URL = "workflow-manager/#/jobs"; 
	public static String NAV_ID = "menu_jobs";

	public JobStatusPage(WebDriver driver) {
		if (!ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not the correct page, current page is: "
							+ driver.getCurrentUrl());
		}
	}

	public static boolean ValidPage(WebDriver driver) {
		log.debug("Current Title:" + driver.getTitle() + "  Desired:"+ PAGE_TITLE +" "+driver.getCurrentUrl() +"  "+Utils.checkIDExists(driver, "jobTable") );
		return driver.getTitle().startsWith(PAGE_TITLE) && driver.getCurrentUrl().contains(PAGE_URL) && Utils.checkIDExists(driver, "jobTable");
	}
	
	public static JobStatusPage getJobStatusPage(WebDriver driver) {
		//must be on homepage
		if (!HomePage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not Home Page of logged in user, current page"
							+ "is: " + driver.getCurrentUrl());
		}
		// click the nav link
		Utils.safeClickById(driver,JobStatusPage.NAV_ID);

		// Wait for the login page to load, timeout after 10 seconds
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return JobStatusPage.ValidPage(d);
			}
		});

		return new JobStatusPage(driver);
	}
	
	public List<WebElement> getJobsTableRows(WebDriver driver) {
		WebElement jobsTable = driver.findElement(By.id("jobTable"));
		Assert.assertNotNull(jobsTable);
		List<WebElement> tableRows = jobsTable.findElements(By.tagName("tr"));

		//includes table header - th
		log.info("sessions table rows length (includes th): {}", tableRows.size());
		//make sure we at least have the th
		Assert.assertTrue(tableRows.size() >= 1);
		
		return tableRows;
	}

	public static String[] getjobTableRow(WebDriver driver,int idx){
		log.debug("[JobStatusPage] getjobTable");
		if (!JobStatusPage.ValidPage(driver)) {
			throw new IllegalStateException(
					"This is not the correct page, current page"
							+ "is: " + driver.getCurrentUrl() +" should be " +JobStatusPage.PAGE_URL);
		}
		List<WebElement> rows = driver.findElements(By.xpath("//*[@id='jobTable']/tbody/tr"));
		if(rows.size() == 0){
			log.error("[JobStatusPage] No jobs in the jobsTable");
			return null;
		}
		WebElement job = rows.get(idx);//first element should be most recent job
		log.debug("[JobStatusPage] getjobTable job row [{}] : " + job.getText(),idx);
		//Id 	PipelineName 	StartDate 	EndDate 	Status 	Progress 	DetailedProgress
		List<WebElement> columns = job.findElements(By.tagName("td"));
		String[] ret =  {columns.get(0).getText(),columns.get(1).getText(),columns.get(2).getText(),columns.get(3).getText(),columns.get(4).getText(),columns.get(5).getText(),columns.get(6).getText()};
		return ret;
	}

}
