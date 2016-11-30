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
package org.mitre.mpf.wfm;

import org.junit.*;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.wfm.ui.*;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mitre.mpf.wfm.ui.Utils.safeSelectUiSelectByIndex;


/***
 * Follows the Page Object Design Pattern
 * http://www.seleniumhq.org/docs/06_test_design_considerations.jsp#chapter06-reference
 *
 * mvn -Dtest=ITWebUI test  if running tomcat locally
 * mvn -Dhttp.proxyHost=gateway.mitre.org -Dhttp.proxyPort=80 -Dtest=ITWebUI test if proxy
 * mvn verify -Dtest=none -DfailIfNoTests=false -Dit.test=ITWebUI#testAddNodeConfigurationPage for individual tests
 * mvn -Dtest=ITWebUI#testLogin test
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITWebUI {
	private static final Logger log = LoggerFactory.getLogger(ITWebUI.class);
	private static int testCtr = 0;

	private static String MPF_USER = "mpf";
	private static String MPF_USER_PWD = "mpf123";
	private static String ADMIN_USER = "admin";
	private static String ADMIN_USER_PWD = "mpfadm";

	protected String base_url = Utils.BASE_URL + "/workflow-manager/";
	protected String node_mgr_url = "http://localhost:8008/";
	protected String TEST_PIPELINE_NAME = "OCV FACE DETECTION PIPELINE";
	protected String TEST_PIPELINE_LONG_NAME = "UNIVERSAL DETECTION PIPELINE";

	private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second &
													// 60 seconds/minute.

	protected static WebDriver driver;
	protected static LoginPage loginPage;
	protected static HomePage homePage;
	protected static FirefoxProfile firefoxProfile;

	protected static WebDriver getDriver() {
		WebDriver driver = null;
		driver = new FirefoxDriver(firefoxProfile);
		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
		driver.manage().window().setSize(new Dimension(1600, 900)); // Xvfb window size .maximize();does not work in Xvfb for headless

		//driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
		//driver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
		//driver.manage().timeouts().setScriptTimeout(30, TimeUnit.SECONDS);

		return driver;
	}

	// is this running on Jenkins?
	protected static boolean jenkins = false;
	static {
		String prop = System.getProperty("jenkins");
		if (prop != null){
			jenkins = Boolean.valueOf(prop);
		}
	}

	private static boolean test_ready = true;
	private static boolean needsCleanup = true;

	@BeforeClass
	public static void openBrowser() {
		//create a browser driver
		firefoxProfile = new FirefoxProfile();
		firefoxProfile.setPreference("browser.private.browsing.autostart", true);// for opening more than one driver/browser at a time
		firefoxProfile.setPreference("accessibility.blockautorefresh", true);// block autorefresh on the nodemanager :8008 page		
		firefoxProfile.setPreference("browser.cache.disk.enable", false);// clear cache
		firefoxProfile.setPreference("browser.cache.memory.enable", false);
		firefoxProfile.setPreference("browser.cache.offline.enable", false);
		firefoxProfile.setPreference("network.http.use-cache", false);
		firefoxProfile.setPreference("network.proxy.type", 0);//circumvent the proxy when hitting localhost
		driver = ITWebUI.getDriver();
	}

	@AfterClass
	public static void closeBrowser() {
		log.info("Stopping the test");
		// Close the browser
		driver.quit();
	}

	@Before
	public void beforeTest() throws InterruptedException{
		log.info("[BeforeTest]");
		needsCleanup = true;
	}

	@After
	public void afterTest() throws InterruptedException{
		log.info("[AfterTest]");
		if (needsCleanup){
			goHome();
			// try to log out (in some cases we may already be logged out)
			loginPage = homePage.logout(driver);
		}
	}

	public void startTest(String testname, String user, String user_pwd) {
		testCtr++;
		log.info("Beginning test #{} {}", testCtr, testname);
		homePage = gotoHomePage(user, user_pwd);
	}

	public void endTest(String testname) {
		goHome();
		log.info("Finished test #{} {}", testCtr, testname);
	}

	//Log the user in and goto home page
	protected HomePage gotoHomePage(String user, String user_pwd) {
		log.info("gotoHomePage base_url:"+base_url);
		// And now use this to visit the site
		driver.get(base_url+"login");
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return LoginPage.ValidPage(d);
			}
		});
		loginPage = new LoginPage(driver);
		homePage = loginPage.loginValidUser(driver, user, user_pwd);
		return homePage;
	}

	protected void goHome() {
		driver.get(base_url);
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return HomePage.ValidPage(d);
			}
		});
	}


	/////////////////////////////////////////////////
	//TESTS
	/////////////////////////////////////////////////
	@Test(timeout = 1 * MINUTES)
	public void testCreateJob() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testCreateJob";
		startTest(testname, MPF_USER, MPF_USER_PWD);

		// start a job
		CreateJobPage jobs_page = CreateJobPage.getCreateJobPage(driver);
		log.info(CreateJobPage.PAGE_URL);
		//Utils.safeClickById(driver, "btn-display-upload-root");

		Thread.sleep(3000);// wait for it to populate

		// see if there is already media, if not create some
		//if (!jobs_page.mediaExists(driver,base_url)) {
			log.info("Need to create some media");
			goHome();
			log.info("At page {}", driver.getCurrentUrl());
			// create some media
			UploadMediaPage page = UploadMediaPage.getUploadMediaPage(driver);
			Assert.assertTrue(UploadMediaPage.ValidPage(driver));
			log.info("Upload Media Page: {}", driver.getCurrentUrl());
			String img_url = page.uploadMediaFromUrl(driver, base_url,Utils.IMG_URL);
			log.info("img_url {}", img_url);
			goHome();
			jobs_page = CreateJobPage.getCreateJobPage(driver);
	//	}
		log.info("Creating Job");
		JobStatusPage job_status_page = jobs_page.createJobFromUploadedMedia(driver, base_url, Utils.IMG_NAME,TEST_PIPELINE_LONG_NAME, 1 ,1);//high priority
		log.info("Job Created on JobStatus Page:"+JobStatusPage.ValidPage(driver));

		Assert.assertTrue(JobStatusPage.ValidPage(driver));
		log.info("Job Created");
		goHome();
		endTest(testname);
		test_ready=true;
	}
/*
	UploadMediaPage page = UploadMediaPage.getUploadMediaPage(driver);
	Assert.assertTrue(UploadMediaPage.ValidPage(driver));
	log.info("Upload Media Page: {}", driver.getCurrentUrl());

	String img_url = page.uploadMediaFromUrl(driver, base_url,Utils.IMG_URL);
	log.info("img_url {}", img_url);

	Assert.assertTrue(img_url.length() > 0);
*/

	//1. This submits a job using media from a url to ocv face detection
	//2. Cancels that job
	//3. Resubmit that job once waiting for the status to appear as CANCELLED in the Web UI
	//4. Waits for the job to get back to an IN_PROGRESS state
	@Test(timeout = 10 * MINUTES)
	public void testCreateJobFromUrlCancelResubmit() throws Exception {
		// an assumption failure causes the test to be ignored;
		// only run this test on a machine where http://somehost-mpf-4.mitre.org is accessible
		needsCleanup = jenkins;
		Assume.assumeTrue("Skipping test. It should only run on Jenkins.", jenkins);

		if(!test_ready) return;
		test_ready=false;
		String testname = "testCreateJobFromUrlCancelResubmit";
		startTest(testname, MPF_USER, MPF_USER_PWD);

		goHome();
		log.info("At page {}", driver.getCurrentUrl());
		//go to the upload page
		UploadMediaPage upload_page = UploadMediaPage.getUploadMediaPage(driver);
		Assert.assertTrue(UploadMediaPage.ValidPage(driver));
		log.info("Upload Media Page: {}", driver.getCurrentUrl());
		log.info("Creating Job from url");
		upload_page.uploadMediaFromUrl(driver, base_url,"http://somehost-mpf-4.mitre.org/rsrc/datasets/samples/face/new_face_video.avi");

		CreateJobPage jobs_page = CreateJobPage.getCreateJobPage(driver);
		log.info(CreateJobPage.PAGE_URL);
		jobs_page = CreateJobPage.getCreateJobPage(driver);
		log.info("Creating Job");
		jobs_page.createJobFromUploadedMedia(driver, base_url,"new_face_video.avi", TEST_PIPELINE_LONG_NAME, 9,1);//high priority
		log.info("Job Created on JobStatus Page:"+JobStatusPage.ValidPage(driver));
		//wait a second - go home and then back to the jobs status page
		Thread.sleep(1000);
		log.info("Going home");
		goHome();
		log.info("Going to job status");
		//the page should be redirected after creating the job from the url, but navigate anyway to grab the JobStatusPage
		final JobStatusPage job_status_page = JobStatusPage.getJobStatusPage(driver);

		log.info("Job Created and on JobStatus Page:"+JobStatusPage.ValidPage(driver));
		Assert.assertTrue(JobStatusPage.ValidPage(driver));
		log.info("Job Created from url");

		//get the job status table rows
		List<WebElement> tableRows = job_status_page.getJobsTableRows(driver);
		//make sure we have the th and a tr
		Assert.assertTrue(tableRows.size() >= 2);
		//get the first td of the first non th tr and display the job id - the job id could be used later to verify
		//the current row for cancellation and resubmission
		WebElement latestJobIdCell = tableRows.get(1).findElement(By.tagName("td"));
		String jobid = latestJobIdCell.getText();
		log.info("latestJobIdCell: {}", jobid);

		Thread.sleep(2000);

		//need to wait till this is IN_PROGRESS
		log.info("Waiting 240 sec for IN_PROGRESS job");
		(new WebDriverWait(driver, 240)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String status=driver.findElement(By.xpath("//table[@id='jobTable']/tbody/tr[1]/td[5]")).getText();
				//List<WebElement> tableRows = job_status_page.getJobsTableRows(d);
				//WebElement jobStatusCell = tableRows.get(1).findElement(By.id("jobStatusCell"+jobid));
				//boolean result = (jobStatusCell != null && jobStatusCell.getText().startsWith("IN_PROGRESS"));
				boolean result = (status != null && status.equals("IN_PROGRESS"));
				if(!result) {
					try {
						log.info("Sleep 2000");
						Thread.sleep(2000);
					} catch (InterruptedException ex) {
						log.error("Threading wait error", ex);
					}
				}
				return result;
			}
		});
		tableRows = job_status_page.getJobsTableRows(driver);
		WebElement jobStatusCell = tableRows.get(1).findElement(By.id("jobStatusCell"+jobid));
		Assert.assertTrue(jobStatusCell.getText().startsWith("IN_PROGRESS"));

		log.info("jobStatusCell cell text: {}", jobStatusCell.getText());
		log.info("Job is now in an 'IN_PROGRESS' state");

		//now click the cancel button
		log.info("Clicking cancel");
		WebElement cancelButton = tableRows.get(1).findElement(By.id("cancelBtn"+jobid));
		Assert.assertNotNull(cancelButton);
		cancelButton.click();

		//wait a second for the modal to appear
		Thread.sleep(1000);
		//get the modal cancel button and click
		WebElement cancelModalCancelButton = driver.findElement(By.id("btnAcceptJobCancellation"));
		Assert.assertNotNull(cancelModalCancelButton);
		cancelModalCancelButton.click();
		log.info("Cancelled job");

		//wait a couple seconds for the ui to update
		Thread.sleep(2000);

		//try to resubmit the job
		//have to grab the table rows again, they are removed from the dom after the modal
		tableRows = job_status_page.getJobsTableRows(driver);
		Assert.assertNotNull(tableRows);
		//make sure we have the th and a tr
		Assert.assertTrue(tableRows.size() >= 2);

		Thread.sleep(1000);

		//need to wait till this is job is terminal - CANCELLED state
		log.info("Waiting for CANCELLED job");
		//(new WebDriverWait(driver, 6000)).until(new ExpectedCondition<Boolean>() {
		//	public Boolean apply(WebDriver d) {
		boolean result = false;
		int count = 0;
		while(count < 100 && !result ) {
			String status=driver.findElement(By.xpath("//table[@id='jobTable']/tbody/tr[1]/td[5]")).getText();
			//log.info("ss:"+status1);
			//String status = driver.findElement(By.id("jobTable")).findElements(By.tagName("tr")).get(1).findElement(By.id("jobStatusCell" + jobid)).getText();
			result = (status != null && status.equals("CANCELLED"));
			//List<WebElement> tableRows1 = job_status_page.getJobsTableRows(driver);
			//List<WebElement> tableRows1 = driver.findElement(By.id("jobTable")).findElements(By.tagName("tr"));
			//log.info("Table rows: "+ tableRows1.size());
			//WebElement jobStatusCell1 = tableRows1.get(1).findElement(By.id("jobStatusCell" + jobid));
			//log.info("Element: "+ jobStatusCell1.getText());
			//result = (jobStatusCell1 != null && jobStatusCell1.getText().equals("CANCELLED"));
			log.info("Result: "+ result + " Status:"+status);
			if (!result) {
				try {
					log.info("Waiting 5 sec");
					Thread.currentThread().sleep(5000);
				} catch (InterruptedException ex) {
					log.error("Threading wait error", ex);
				}
			}
			count ++;
		}
		Assert.assertTrue(count < 100);
		tableRows = job_status_page.getJobsTableRows(driver);
		jobStatusCell = tableRows.get(1).findElement(By.id("jobStatusCell"+jobid));
		Assert.assertTrue(jobStatusCell.getText().equals("CANCELLED"));

		log.info("jobStatusCell cell text: {}", jobStatusCell.getText());
		log.info("Job is in a 'CANCELLED' state");

		//wait a 10 seconds for the cancellation to persist - the atmosphere message
		//is sent to the ui and updates are made to the ui before the data is persisted
		//resubmitting before db persistence will cause a failure
		log.info("Waiting 10sec for updates to UI");
		Thread.sleep(10000);

		//TODO: may want to verify the job id is still the correct row at this time - a new job could be
		//submitted while this test is in progress
		WebElement resubmitButton = tableRows.get(1).findElement(By.id("resubmitBtn"+jobid));
		Assert.assertNotNull(resubmitButton);
		log.info("btnDisplayResubmitJobModal cell text: {}", resubmitButton.getText());
		//now click to display the resubmit modal
		resubmitButton.click();

		//get the display resubmit modal button and click it to display the resubmit modal
		log.info("Waiting for Modal");
		(new WebDriverWait(driver, 240)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				WebElement ele = driver.findElement(By.id("resubmitBtn"+jobid));
				return ele != null;
			}
		});
		WebElement resubmitModalResubmitButton= driver.findElement(By.id("resubmitBtn"+jobid));
		Assert.assertNotNull(resubmitModalResubmitButton);
		log.info("Approving modal resubmit");
//		resubmitModalResubmitButton.click();	// ToDo: P038: this causes the test to fail, and seems to not be necessary

		Thread.sleep(3000);
		log.info("now set the priority to 9 (highest priority)");
		boolean success = safeSelectUiSelectByIndex( driver, "jobPrioritySelectServer", 9 );

		//get the confirm resubmit button in the modal and click it to resubmit
		WebElement resubmitJobModalResubmitButton = driver.findElement(By.id("btnResubmitJobModalResubmit"));
		Assert.assertNotNull(resubmitJobModalResubmitButton);
		resubmitJobModalResubmitButton.click();
		log.info("Resubmitted job");

		//verify the job is in progress again to know that resubmission has worked properly
		//have to grab the table rows again, they are removed from the dom after the modal
		tableRows = job_status_page.getJobsTableRows(driver);
		Assert.assertNotNull(tableRows);
		//make sure we have the th and a tr
		Assert.assertTrue(tableRows.size() >= 2);

		//need to wait till this is in progress again - IN_PROGRESS
		log.info("Waiting 240 sec for IN_PROGRESS job");
		(new WebDriverWait(driver, 240)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String status=driver.findElement(By.xpath("//table[@id='jobTable']/tbody/tr[1]/td[5]")).getText();
				boolean result = (status != null && status.equals("IN_PROGRESS"));
				if(!result) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ex) {
						log.error("Threading wait error", ex);
					}
				}
				return result;
			}
		});
		tableRows = job_status_page.getJobsTableRows(driver);
		jobStatusCell = tableRows.get(1).findElement(By.id("jobStatusCell"+jobid));
		log.info("jobStatusCell cell text: {}", jobStatusCell.getText());
		log.info("Job should be back in an 'IN_PROGRESS' state");
		Assert.assertTrue(jobStatusCell.getText().startsWith("IN_PROGRESS") || jobStatusCell.getText().startsWith("COMPLETE"));
		log.info("The resubmitted job is now back in progress!");

		endTest(testname);
		test_ready=true;
	}

	// If you try to log in as "mpf" in one browser while the "mpf" user is
	// already logged in using another browser the first browser session will
	// end and redirect back to the login page with error message stating that
	// the user logged in from another location.
	// The same is true for the "admin" user. Note that you can bypass the login
	// screen on the same browser if you're already logged in one tab and go to
	// a non-login page in another tab.
	/**
	@Test(timeout = 1 * MINUTES)
	public void testDualLogin() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		final String err_msg = "You've been logged out because the same user logged in from another location.";
		final String timeout_msg = "Session timed out or expired.";
		String testname = "testDualLogin";
		testCtr++;
		log.info("Beginning test #{} {}", testCtr, testname);
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		Assert.assertTrue(HomePage.ValidPage(driver));
		log.info("First Browser logged in to homepage, starting second browser");

		// start another browser and log out first		
		WebDriver driver2 = ITWebUI.getDriver();
		driver2.get(base_url);
		// Wait for the page to load, timeout after 10 seconds
		(new WebDriverWait(driver2, 10))
				.until(new ExpectedCondition<Boolean>() {
					public Boolean apply(WebDriver d) {
						return d.getTitle().startsWith(LoginPage.PAGE_TITLE);
					}
				});

		LoginPage loginPage2 = new LoginPage(driver2);
		log.info("Browser2 loginpage");

		Thread.sleep(3000);//wait

		HomePage homePage2 = loginPage2.loginValidUser(driver2, MPF_USER,
				MPF_USER_PWD);
		log.info("Home page on second browser");
		//need to fire an ajax call to force bootout
		NodesAndProcessPage.getNodesAndProcessPage(driver2);
		// click the nav link
		Utils.safeClickById(driver2, NodesAndProcessPage.NAV_ID);

		log.info("Waiting for bootout");
		// wait for first browser to be at login screen with message
		(new WebDriverWait(driver, 30)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return d.getTitle().startsWith(LoginPage.PAGE_TITLE);
			}
		});
		// check error message
		log.info("Should be on login page on first browser. Waiting for error message.");
		(new WebDriverWait(driver, 20)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				log.info("waiting....");
				WebElement element = null;
				try{
					element = d.findElement(By.className("error"));
				}catch(NoSuchElementException nse){
					log.info("no error element");
				}
				if(element == null){
					log.info("no error");
					try{
						element = d.findElement(By.className("msg"));
					}catch(NoSuchElementException nse){
						log.info("no msg element");
					}
				}
				if(element == null){
					log.info("no element found");
					return false;
				}
				log.info("error msg:"+element.getText());
				return (element.getText().startsWith(err_msg) || element.getText().startsWith(timeout_msg));
			}
		});

		Assert.assertTrue(true);
		log.info("Closing second broswer");
		driver2.close();
		driver.close();
		log.info("Opening new browser and going home");
		//create new driver
		driver = ITWebUI.getDriver();
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		endTest(testname);
		test_ready=true;
	}
*/
	@Test(timeout = 1 * MINUTES)
	public void testHome() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testHome";
		testCtr++;
		log.info("Beginning test #{} {}", testCtr, testname);
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		Assert.assertTrue(HomePage.ValidPage(driver));
		endTest(testname);
		test_ready=true;
	}

	@Test(timeout = 1 * MINUTES)
	public void testHomeNav() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testHomeNav";
		testCtr++;
		log.info("Beginning test #{} {}", testCtr, testname);
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		log.info("Checking Nav Tags");
		String[] navtagsids = { JobStatusPage.NAV_ID, UploadMediaPage.NAV_ID,
				CreateJobPage.NAV_ID, "menu_media_markup_results",
				"menu_adminDashboard", NodesAndProcessPage.NAV_ID,
				NodeConfigurationPage.NAV_ID, "menu_adminPropertySettings",
				LogsPage.NAV_ID, "menu_adminStatistics", "menu_swaggerui" };
		log.info("Checking Nav Tags : "+navtagsids.length);
		for (String tagid : navtagsids) {
			log.info("Checking tag {}", tagid);
			Assert.assertTrue(Utils.checkIDExists(driver, tagid));
		}
		endTest(testname);
		test_ready=true;
	}


	//After creating a job you will be automatically directed to the Job Status view. Make sure the jobs table Progress column continues to be updated as the job is executed.
	//Switch back and forth between the Session and All jobs listing and ensure that the Progress column is correct and is being updated for the job in each list.
	//Switch to another view and then switch back to the Job Status view and ensure the column correct in each list.
	//Log out and then log back in and ensure the column is correct for the All jobs listing. Note that when logging back in the Session jobs listing should be empty.
	//You should use a non-trivial job that runs at least a few minutes for this.
	//Additionally, ensure that an alert popup appears when the job is complet
	/*
	@Test(timeout = 10 * MINUTES)
	public void testJobPolling() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testJobPolling";
		startTest(testname, MPF_USER, MPF_USER_PWD);
		SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yy hh:mm aaa"); // 12/16/15 2:24 PM
		int num_media = 2; //number of videos to process, we need enough time for a few minutes

		CreateJobPage jobs_page = CreateJobPage.getCreateJobPage(driver);


		Utils.safeClickById(driver, "btn-display-upload-root");// should have media checked
		Thread.sleep(4000);// wait for it to populate
		// upload media
		// see if there is already media, if not create some
		log.info("[testJobPolling] Creating media [{}]",num_media );
		for(int i=0; i< num_media;i++){
		  UploadMediaPage page = UploadMediaPage.getUploadMediaPage(driver);
		  Assert.assertTrue(UploadMediaPage.ValidPage(driver));
		  log.info("[testJobPolling] Upload Media Page: {}", driver.getCurrentUrl()); String
		  img_url = page.uploadMediaFromUrl(driver, base_url,Utils.VIDEO_URL);
		  log.info("[testJobPolling]  img_url {}", img_url);
		  Assert.assertTrue(img_url.length() > 0);
		  //go back to jobs page
		  jobs_page = CreateJobPage.getCreateJobPage(driver);
		}

		// create the low priority job to run
		Date job1_start = new Date();
		JobStatusPage job_status_page = jobs_page.createJobFromUploadedMedia(driver, base_url, TEST_PIPELINE_LONG_NAME, "1",num_media);//low priority

		// verify the status is there in the jobsTable  Id 	Pipeline Name 	Start Date 	End Date 	Status 	Progress 	Detailed Progress
		String[] job1 = JobStatusPage.getjobTableRow(driver,0);
		log.info("[testJobPolling] testing job:"+job1[1] +" = "+TEST_PIPELINE_LONG_NAME);
		Assert.assertTrue(job1[1].equals(TEST_PIPELINE_LONG_NAME));//does job pipeline same as what we started

		Date date = fmt.parse(job1[2]);//parse the start date to compare to the table data
		Long time = job1_start.getTime() - date.getTime();
		log.info("[testJobPolling] Cur Time {},  Job Execution Time {}  Diff:{}",fmt.format(job1_start), fmt.format(date), time);
		Assert.assertTrue(time < 100000);//check thats its recently created

		(new WebDriverWait(driver, 60)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver,0);
				return job[4].equals("IN_PROGRESS"); //check status
			}
		});

		//it should be in the all jobs table
		log.info("[testJobPolling] Checking AllJobs");
		Utils.safeClickById(driver,"btnAllJobs");
		Thread.sleep(3000);
		String[] job2 = JobStatusPage.getjobTableRow(driver,0);
		Assert.assertTrue(job2[0].equals(job1[0]));//check id
		Assert.assertTrue(job2[4].equals("IN_PROGRESS"));//check status

		//logout then back in
		log.info("[testJobPolling] Checking logout/login");
		goHome();
		loginPage = homePage.logout(driver);
		Assert.assertTrue(loginPage.ValidPage(driver));
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		Assert.assertTrue(HomePage.ValidPage(driver));

		job_status_page = JobStatusPage.getJobStatusPage(driver);
		//job should be in the all jobs table and not session
		log.info("[testJobPolling] Checking Job Status - Session");
		Utils.safeClickById(driver,"btnSessionJobs");
		Thread.sleep(2000);
		String[] job3 = JobStatusPage.getjobTableRow(driver,0);
		Assert.assertTrue(job3 == null);

		//it should be in the all jobs table
		log.info("[testJobPolling] Checking Job Status - All");
		Utils.safeClickById(driver,"btnAllJobs");
		Thread.sleep(3000);
		String[] job4 = JobStatusPage.getjobTableRow(driver,0);
		Assert.assertTrue(job4[0].equals(job1[0]));//check id

		int max = 10*60; //seconds
		log.info("[testJobPolling] wait for job to complete (max:{} sec)",max);

		(new WebDriverWait(driver, max)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver,0);
				try {
					Thread.sleep(2000);
				}catch(InterruptedException ex){
					log.error(ex.toString());
				}
				//if the column changes this will need to be 
				return job[6].equals("100%"); //check status
			}
		});
		log.info("[testJobPolling] job complete");

		//check popup
		log.info("[testJobPolling] Checking popup!");
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return Utils.checkClassExists(d, "noty_message");
			}
		});
		WebElement noty_text = driver.findElement(By.className("noty_text"));
		log.info("[testJobPolling] Popup text: {}",noty_text.getText());
		Assert.assertTrue(noty_text.getText().equals("Job "+job1[0]+" is now complete!"));//Job 124 is now complete!

		//click the ok button on dialog window
		Utils.safeClickById(driver,"button-0");
		Thread.sleep(3000);
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return !Utils.checkClassExists(d, "noty_message");
			}
		});


		endTest(testname);
		test_ready=true;
	}
*/

	//1) Go to the Create Job / View media view. Create and set job priority 10 on a job, and verify that the workflow manager log file shows the job being run at that priority
	//2) Also, run two competing jobs, one at priority 1 and the other at priority 10, with the lower priority starting first. Ensure that the higher priority job finishes before the lower priority job.
	//The execution times of the jobs should be non-trivial (at least a few minutes) in order to see this behavior.
	/*TODO Takes too long to complete
	@Test(timeout = 10 * MINUTES)
	public void testJobPriority() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testJobPriority";
		int max = 10*60; //seconds
		startTest(testname, MPF_USER, MPF_USER_PWD);
		SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yy hh:mm aaa"); // 12/16/15 2:24 PM
		int num_media = 1; //number of videos to process, we need enough time for activemq to handle the priority so need some videos to run for a few minutes

		// upload media
		CreateJobPage jobs_page = CreateJobPage.getCreateJobPage(driver);
		Utils.safeClickById(driver, "btn-display-upload-root");// Click on view uploaded media
		Thread.sleep(4000);// wait for it to populate
		// see if there is already media, if not create some
		log.info("[testJobPriority] Creating media [{}]",num_media );
		for(int i=0; i< num_media;i++){
			UploadMediaPage page = UploadMediaPage.getUploadMediaPage(driver);
			Assert.assertTrue(UploadMediaPage.ValidPage(driver));
			log.info("[testJobPriority] Upload Media Page: {}", driver.getCurrentUrl()); String
					img_url = page.uploadMediaFromUrl(driver, base_url,Utils.LONG_VIDEO_URL);
			log.info("[testJobPriority]  img_url {}", img_url);
			Assert.assertTrue(img_url.length() > 0);
			//go back to jobs page
			jobs_page = CreateJobPage.getCreateJobPage(driver);
		}

		// create the low priority job to run
		Date job1_start = new Date();
		JobStatusPage job_status_page = jobs_page.createJobFromUploadedMedia(driver, base_url, TEST_PIPELINE_LONG_NAME, "1",num_media);//low priority
		// verify the status is there in the jobsTable  Id 	Pipeline Name 	Start Date 	End Date 	Status 	Progress 	Detailed Progress
		String[] row_low_priority = JobStatusPage.getjobTableRow(driver,0);
		Assert.assertTrue(row_low_priority[1].equals(TEST_PIPELINE_LONG_NAME));//does job pipeline same as what we started
		Date date = fmt.parse(row_low_priority[2]);//parse the start date to compare to the table data
		Long time = job1_start.getTime() - date.getTime();
		log.info("[testJobPriority] Cur Time {},  Job Execution Time {}  Diff:{}",fmt.format(job1_start), fmt.format(date), time);
		Assert.assertTrue(time < 100000);//check thats its recently created
		//make sure it is in progress
		log.info("[testJobPriority] wait for low-priority to start (max:{} sec)", max);
		(new WebDriverWait(driver, max)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver, 0);
				return job[4].equals("IN_PROGRESS"); //check status
			}
		});

		// create the high priority job to run while low priority is running
		jobs_page = CreateJobPage.getCreateJobPage(driver);
		job_status_page = jobs_page.createJobFromUploadedMedia(driver, base_url, TEST_PIPELINE_LONG_NAME, "9",num_media);//high priority
		// verify the status is there in the jobsTable
		String[] row_high_priority = JobStatusPage.getjobTableRow(driver,0);
		Assert.assertTrue(!row_high_priority[0].equals(row_low_priority[0]));
		Assert.assertTrue(row_high_priority[1].equals(TEST_PIPELINE_LONG_NAME));//does job pipeline same as what we started
		date = fmt.parse(row_high_priority[2]);//parse the start date to compare to the table data
		time = job1_start.getTime() - date.getTime();
		log.info("[testJobPriority] Cur Time {},  Job Execution Time {}  Diff:{}",fmt.format(job1_start), fmt.format(date), time);
		Assert.assertTrue(time < 100000);//check thats its recently created
		//make sure it is running
		log.info("[testJobPriority] waiting for high priority to start(max:{} sec)", max);
		(new WebDriverWait(driver, max)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver,0);
				return job[4].equals("IN_PROGRESS"); //check status
			}
		});

		//wait for the high priority to complete and compare with the low priority]

		log.info("[testJobPriority] wait for high  priority to complete (max:{} sec)",max);
		(new WebDriverWait(driver, max)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver,0);
				return job[4].equals("COMPLETE"); //check status
			}
		});

		log.info("verifying the low priority job and make sure it is still running");
		String[] row_low_priority1 = JobStatusPage.getjobTableRow(driver,1);
		Assert.assertTrue(row_low_priority1[4].equals("IN_PROGRESS")); //low priority show still be in progress
		log.info("verified");
		Assert.assertTrue(JobStatusPage.ValidPage(driver));
		endTest(testname);
		test_ready=true;
	}*/



	@Test(timeout = 1 * MINUTES)
	public void testLogPage() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testLogPage";
		startTest(testname, ADMIN_USER, ADMIN_USER_PWD);
		LogsPage page = LogsPage.getLogsPage(driver);
		Assert.assertTrue(LogsPage.ValidPage(driver));
		endTest(testname);
		test_ready=true;
	}


	// TEST Login
	@Test(timeout = 1 * MINUTES)
	public void testLogin() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testLogin";
		testCtr++;
		log.info("Beginning test #{} {}", testCtr, testname);
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		Assert.assertTrue(HomePage.ValidPage(driver));
		endTest(testname);
		test_ready=true;
	}
	// TEST Login
	@Test(timeout = 1 * MINUTES)
	public void testLogout() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testLogout";
		testCtr++;
		log.info("Beginning test #{} {}", testCtr, testname);
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		loginPage = homePage.logout(driver);
		Assert.assertTrue(loginPage.ValidPage(driver));
		homePage = gotoHomePage(MPF_USER, MPF_USER_PWD);
		endTest(testname);
		//log.info("Finished test #{} {}", testCtr, testname);
		test_ready=true;
	}

	/* TODO problem on Jenkins machines
	@Test(timeout = 1 * MINUTES)
	public void testNodesAndProcessPage() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testNodesAndProcessPage";
		startTest(testname, ADMIN_USER, ADMIN_USER_PWD);
		NodesAndProcessPage page = NodesAndProcessPage.getNodesAndProcessPage(driver);
		Assert.assertTrue(NodesAndProcessPage.ValidPage(driver));

		Thread.sleep(5000);// wait for a few to let the system stabilize
		// get list of services from mpf
		List<String> elements = page.getCurrentNodesAndProcess(driver);
		// get list of services from node manager interface
		List<String> nodes = page.getCurrentNodesAndProcessFromNodeManager(driver, node_mgr_url);
		// compare the lists
		for (String ele : elements) {
			log.info("[testNodesAndProcessPage] NodesAndProcessService: " + ele);
			boolean found = false;
			for (String node : nodes) {
				log.info("[testNodesAndProcessPage] Node Manager Service: "+ node);
				if (ele.endsWith(node)) {
					found = true;
					break;
				}
			}
			if (!found)log.error("Service not found: " + ele);
			Assert.assertTrue(found);
		}
		endTest(testname);
		test_ready=true;
	}
	*/

	/*
	TODO fails on jenkins	
	timeout - maybe push isnt updating the table status
	@Test(timeout = 1 * MINUTES)
	public void testNodesAndProcessStopAndStartPage() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testNodesAndProcessStopAndStartPage";
		startTest(testname, ADMIN_USER, ADMIN_USER_PWD);
		NodesAndProcessPage page = NodesAndProcessPage
				.getNodesAndProcessPage(driver);
		Assert.assertTrue(NodesAndProcessPage.ValidPage(driver));

		// get list of services from mpf
		List<String> elements = page.getCurrentNodesAndProcess(driver);
		// stop the first service
		String first = elements.get(0).substring(0,
				elements.get(0).lastIndexOf(":"));// remove status
		log.info("First Node:" + first);
		Assert.assertTrue(page.stopNode(driver, first));
		Thread.sleep(3000);// wait
		Assert.assertTrue(page.startNode(driver, first));
		Thread.sleep(3000);// wait; don't quit immediately or all the services fail
		endTest(testname);
		test_ready=true;
	}*/

	@Test(timeout = 1 * MINUTES)
	public void testNodeConfigurationPage() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testNodeConfigurationPage";
		startTest(testname, ADMIN_USER, ADMIN_USER_PWD);
		NodeConfigurationPage page = NodeConfigurationPage
				.getNodeConfigurationPage(driver);
		Assert.assertTrue(NodeConfigurationPage.ValidPage(driver));

		// get list of services
		int count = page.getCurrentServicesCount(driver);
		log.info("#services:"+count);
		Assert.assertTrue(count > 0);
		endTest(testname);
		test_ready=true;
	}

	/*TODO failing on jenkins causes some components to stop
	@Test(timeout = 1 * MINUTES)
	public void testNodeConfigurationPageAdd() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testNodeConfigurationPageAdd";
		startTest(testname, ADMIN_USER, ADMIN_USER_PWD);
		NodeConfigurationPage page = NodeConfigurationPage.getNodeConfigurationPage(driver);
		Assert.assertTrue(NodeConfigurationPage.ValidPage(driver));

		// get list of services
		int start_count = page.getCurrentServicesCount(driver);
		log.info("num of services:{}", start_count);
		Assert.assertTrue(start_count > 0);
		log.info("Adding service");
		page.addService(driver); //TODO: the logic for this method needs to be updated

		// make sure it is running, go home then come back to refresh list
		log.info("Going home then reloading NodeConfiguration page");
		goHome();
		page = NodeConfigurationPage.getNodeConfigurationPage(driver);
		Assert.assertTrue(NodeConfigurationPage.ValidPage(driver));

		int end_count = page.getCurrentServicesCount(driver);
		log.info("num of services:{} number of services before:{}", end_count,start_count);
		Assert.assertTrue(end_count > start_count);
		endTest(testname);
		test_ready=true;
	}*/
/*
	@Test(timeout = 1 * MINUTES)
	public void testUploadMedia() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testUploadMedia";
		startTest(testname, MPF_USER, MPF_USER_PWD);
		UploadMediaPage page = UploadMediaPage.getUploadMediaPage(driver);
		Assert.assertTrue(UploadMediaPage.ValidPage(driver));
		log.info("Upload Media Page: {}", driver.getCurrentUrl());

		String img_url = page.uploadMediaFromUrl(driver, base_url,Utils.IMG_URL);
		log.info("img_url {}", img_url);

		Assert.assertTrue(img_url.length() > 0);
		endTest(testname);
		test_ready=true;
	}
*/
	@Test(timeout = 1 * MINUTES)
	public void testUrlfetch() throws Exception {
		if(!test_ready) return;
		test_ready=false;
		String testname = "testUrlfetch";
		startTest(testname, MPF_USER, MPF_USER_PWD);
		URL url = new URL(Utils.IMG_URL);
		String newfile = "/tmp/" + UUID.randomUUID().toString();
		log.info("trying Download file:" + url + " to " + newfile);
		org.apache.commons.io.FileUtils.copyURLToFile(url, new File(newfile),8000, 8000);
		Assert.assertTrue(new File(newfile).exists());
		endTest(testname);
		test_ready=true;
	}

}
