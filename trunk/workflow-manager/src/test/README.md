Notes for Running Workflow-Manager's Protractor Tests
=====================================================

These Protractor tests are intended to test end-to-end UI scenarios at the page level.  

However, in this branch, they are also used to test `$scope` models, which should really be done in
 unit tests.  That will happen when unit testing is implemented.

## Pre-Requisites

1.  Install nodeJS (npm is part of this install):
    ```sudo yum install nodejs```
2.  Make sure Google Chrome (and/or other browsers) are up-to-date since Protractor and drivers only work with recent versions:  http://www.protractortest.org/#/browser-support.
3.  Run `npm install` in the test directory, which will download, install/update all plugins specified in the `package.json` file.
4.  Configure the test environment using the variables defined in `environment.js`.  Most of them have good default values, so it is safe to leave them as is (without defining environment variables).  An example where a change is required is, for example, workflow manager needs to run on a different port.

## Run Protractor Tests

There are several ways to run the workflow-manager Protractor tests:

1.  Using `npm`
    1.  Go to the test directory:
        ```cd mpf/trunk/workflow-manage/src/test```
    2.  Run workflow-manager (either in IDE or separately in Tomcat, but note that you need to also have the other MPF system dependencies running, i.e., postgreSQL, NodeManager, etc.).
    3.  Run the npm script to run all tests (using `protractor-flake`)
        * to run all the tests
          ```npm run protractor```
        * to run specific test suite:
          ```npm run protractor -- --specs='protractor/spec/dashboard_page_spec.js'```
2.  Directly with a running Selenium server (easiest way while developing if you need to run individual test suites):
    1.  Run the selenium server:
        ```webdriver-manager start```
    2.  Run workflow-manager (either in IDE or separately in Tomcat, but note that you need to also have the other MPF system dependencies running, i.e., postgreSQL, NodeManager, etc.).
    3.  Go to the test directory:
        ```cd mpf/trunk/workflow-manage/src/test```
    4.  Run Protractor:
        * to run all the tests
          ```protractor protractor/protractor.conf.js```
        * to run specific test suite:  
          ```protractor protractor/protractor.conf.js --specs='protractor/spec/dashboard_page_spec.js'```
        * to run it multiple times (e.g., to see how often it may fail certain tests)
          ```for ((n=0;n<100;n++)); do protractor protractor/protractor.conf.js; echo $n >&2; done > output.txt```
        which will run it 100 times, echoing the iteration after each test (for progress monitoring) and output all output to `output.txt`
2.  Using Maven on a development VM:
    - ```mvn verify -DfailIfNoTests=false -Dweb.rest.protocol="http" -Dtransport.guarantee="NONE" -Dtest=none -Dit.test=maven-ng-protractor -Pprotractor```
3.  Using Maven on a Jenkins CI server:
    - ```mvn verify -DfailIfNoTests=false -Dweb.rest.protocol="http" -Dtransport.guarantee="NONE" -Dtest=none -Dit.test=maven-ng-protractor -Pxvfb,protractor,jenkins -Dspring.profiles.active=jenkins```
