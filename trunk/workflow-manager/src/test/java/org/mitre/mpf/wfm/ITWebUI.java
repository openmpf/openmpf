/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/***
 * Follows the Page Object Design Pattern
 * http://www.seleniumhq.org/docs/06_test_design_considerations.jsp#chapter06-reference
 *
 * mvn -Dtest=ITWebUI test  if running tomcat locally
 * mvn verify -Dtest=none -DfailIfNoTests=false -Dit.test=ITWebUI#testAddNodeConfigurationPage for individual tests
 * mvn -Dtest=ITWebUI#testLogin test
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITWebUI {
	private static final Logger log = LoggerFactory.getLogger(ITWebUI.class);
	private static int testCtr = 0;

	private static String MPF_USER = "mpf";
	private static String MPF_USER_PWD = "mpf123";
	// private static String ADMIN_USER = "admin";
	// private static String ADMIN_USER_PWD = "mpfadm";

	protected String base_url = Utils.BASE_URL + "/workflow-manager/";
	// protected String node_mgr_url = "http://localhost:8008/";
	// protected String TEST_PIPELINE_NAME = "OCV FACE DETECTION PIPELINE";
	// protected String TEST_PIPELINE_LONG_NAME = "UNIVERSAL DETECTION PIPELINE";

	private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

	protected static WebDriver driver;
	protected static LoginPage loginPage;
	protected static HomePage homePage;
	protected static FirefoxProfile firefoxProfile;

	protected static WebDriver getDriver() {
		WebDriver driver = null;
		driver = new FirefoxDriver(firefoxProfile);
		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
		driver.manage().window().setSize(new Dimension(1600, 900)); // Xvfb window size .maximize();does not work in Xvfb for headless
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

	// TODO: Convert the tests below into Protractor tests.
	// NOTE: The web UI has changed since some of these tests last worked.

	/*
	// If you try to log in as "mpf" in one browser while the "mpf" user is
	// already logged in using another browser the first browser session will
	// end and redirect back to the login page with error message stating that
	// the user logged in from another location.
	// The same is true for the "admin" user. Note that you can bypass the login
	// screen on the same browser if you're already logged in one tab and go to
	// a non-login page in another tab.
	@Test(timeout = 1 * MINUTES)
	public void testDualLogin() throws Exception {
		if (!test_ready) return;
		test_ready = false;
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
				try {
					element = d.findElement(By.className("error"));
				} catch (NoSuchElementException nse) {
					log.info("no error element");
				}
				if (element == null) {
					log.info("no error");
					try {
						element = d.findElement(By.className("msg"));
					} catch (NoSuchElementException nse) {
						log.info("no msg element");
					}
				}
				if (element == null) {
					log.info("no element found");
					return false;
				}
				log.info("error msg:" + element.getText());
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
		test_ready = true;
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

	/*
	// After creating a job you will be automatically directed to the Job Status view. Make sure the jobs table Progress column continues to be updated as the job is executed.
	// Switch back and forth between the Session and All jobs listing and ensure that the Progress column is correct and is being updated for the job in each list.
	// Switch to another view and then switch back to the Job Status view and ensure the column correct in each list.
	// Log out and then log back in and ensure the column is correct for the All jobs listing. Note that when logging back in the Session jobs listing should be empty.
	// You should use a non-trivial job that runs at least a few minutes for this.
	// Additionally, ensure that an alert popup appears when the job is complet
	@Test(timeout = 10 * MINUTES)
	public void testJobPolling() throws Exception {
		if (!test_ready) return;
		test_ready = false;
		String testname = "testJobPolling";
		startTest(testname, MPF_USER, MPF_USER_PWD);
		SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yy hh:mm aaa"); // 12/16/15 2:24 PM
		int num_media = 2; //number of videos to process, we need enough time for a few minutes

		CreateJobPage jobs_page = CreateJobPage.getCreateJobPage(driver);


		Utils.safeClickById(driver, "btn-display-upload-root");// should have media checked
		Thread.sleep(4000);// wait for it to populate
		// upload media
		// see if there is already media, if not create some
		log.info("[testJobPolling] Creating media [{}]", num_media);
		for (int i = 0; i < num_media; i++) {
			UploadMediaPage page = UploadMediaPage.getUploadMediaPage(driver);
			Assert.assertTrue(UploadMediaPage.ValidPage(driver));
			log.info("[testJobPolling] Upload Media Page: {}", driver.getCurrentUrl());
			String
					img_url = page.uploadMediaFromUrl(driver, base_url, Utils.VIDEO_URL);
			log.info("[testJobPolling]  img_url {}", img_url);
			Assert.assertTrue(img_url.length() > 0);
			//go back to jobs page
			jobs_page = CreateJobPage.getCreateJobPage(driver);
		}

		// create the low priority job to run
		Date job1_start = new Date();
		JobStatusPage job_status_page = jobs_page.createJobFromUploadedMedia(driver, base_url, TEST_PIPELINE_LONG_NAME, "1", num_media);//low priority

		// verify the status is there in the jobsTable  Id 	Pipeline Name 	Start Date 	End Date 	Status 	Progress 	Detailed Progress
		String[] job1 = JobStatusPage.getjobTableRow(driver, 0);
		log.info("[testJobPolling] testing job:" + job1[1] + " = " + TEST_PIPELINE_LONG_NAME);
		Assert.assertTrue(job1[1].equals(TEST_PIPELINE_LONG_NAME));//does job pipeline same as what we started

		Date date = fmt.parse(job1[2]);//parse the start date to compare to the table data
		Long time = job1_start.getTime() - date.getTime();
		log.info("[testJobPolling] Cur Time {},  Job Execution Time {}  Diff:{}", fmt.format(job1_start), fmt.format(date), time);
		Assert.assertTrue(time < 100000);//check thats its recently created

		(new WebDriverWait(driver, 60)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver, 0);
				return job[4].equals("IN_PROGRESS"); //check status
			}
		});

		//it should be in the all jobs table
		log.info("[testJobPolling] Checking AllJobs");
		Utils.safeClickById(driver, "btnAllJobs");
		Thread.sleep(3000);
		String[] job2 = JobStatusPage.getjobTableRow(driver, 0);
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
		Utils.safeClickById(driver, "btnSessionJobs");
		Thread.sleep(2000);
		String[] job3 = JobStatusPage.getjobTableRow(driver, 0);
		Assert.assertTrue(job3 == null);

		//it should be in the all jobs table
		log.info("[testJobPolling] Checking Job Status - All");
		Utils.safeClickById(driver, "btnAllJobs");
		Thread.sleep(3000);
		String[] job4 = JobStatusPage.getjobTableRow(driver, 0);
		Assert.assertTrue(job4[0].equals(job1[0]));//check id

		int max = 10 * 60; //seconds
		log.info("[testJobPolling] wait for job to complete (max:{} sec)", max);

		(new WebDriverWait(driver, max)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				String[] job = JobStatusPage.getjobTableRow(driver, 0);
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ex) {
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
		log.info("[testJobPolling] Popup text: {}", noty_text.getText());
		Assert.assertTrue(noty_text.getText().equals("Job " + job1[0] + " is now complete!"));//Job 124 is now complete!

		//click the ok button on dialog window
		Utils.safeClickById(driver, "button-0");
		Thread.sleep(3000);
		(new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return !Utils.checkClassExists(d, "noty_message");
			}
		});


		endTest(testname);
		test_ready = true;
	}
	*/

	/*
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
	*/

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

	/*
	// NOTE: When this test fails on jenkins it causes some components to stop
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