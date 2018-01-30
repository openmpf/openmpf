/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestWorkflowManager {

    private static final Logger log = LoggerFactory.getLogger(TestWorkflowManager.class);

//    @Autowired
//    private DataManagementBo dataManagementBo;

    private final IoUtils ioUtils = new IoUtils();

    public final static int MINUTES = 60 * 1000; //60 seconds/minute * 1000 milliseconds/second

    //assert that wfm can create appropriate mimetypes for media

    //test ideas for WFM

    //give a dummy job to the output builder to confirm correct builds

    //some reasonable way to test that Redis is operational

    //route testing

    //

//    @Test
    public void testCreateMediaSetsMimeType() throws Exception {
//        List<String> mediaPaths = new ArrayList<String>();
//        mediaPaths.add("/samples/meds1.jpg");
//        mediaPaths.add("/samples/video_02.mp4");
//
//        List<Media> mediaList = testingMethods.createMediaList(mediaPaths);
//        for(Media medium : mediaList) {
//            Assert.assertNotNull("MediaList contained a null element.", medium);
//            Assert.assertNotNull(String.format("%s did not have its MIME type set.", medium), medium.getMimeType());
//            log.debug("{}", medium);
//        }
    }

    //assert that wfm web page can be properly loaded (more extensively tested in TestUI)

//    @Test
    public void testWfmWeb() throws IOException {
        String wfmUrl = "http://localhost:8081/workflow-manager";
        URL url;
        InputStream inputStream = null;
        BufferedReader bufferedReader;
        String htmlLine;
        boolean success = false;

        url = new URL(wfmUrl);
        inputStream = url.openStream();
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        while ((htmlLine = bufferedReader.readLine()) != null) {
            if(htmlLine.contains("uploadFilesForm")) {
                success = true;
            }
        }

        Assert.assertTrue(success);
    }

    //assert that IOUtils library is properly determining the type of medium

    @Test(timeout = 5 * MINUTES)
    public void detectTests() throws IOException {
        String imageType = ioUtils.getMimeType(this.getClass().getClassLoader().getResourceAsStream("/samples/meds1.jpg"));
        Assert.assertNotNull("The detected audioType must not be null.", imageType);

        String videoType = ioUtils.getMimeType(this.getClass().getClassLoader().getResourceAsStream("/samples/mpeg_vid.mpg"));
        Assert.assertNotNull("The detected audioType must not be null.", videoType);

        String audioType = ioUtils.getMimeType(this.getClass().getClassLoader().getResourceAsStream("/samples/green.wav"));
        Assert.assertNotNull("The detected audioType must not be null.", audioType);
    }

	@Test(timeout = 5 * MINUTES)
    public void getMediaTypeTests() throws WfmProcessingException, MalformedURLException {
        URI uri = ioUtils.findFile("/samples/mpeg_vid.mpg");
	    MediaType type = ioUtils.getMediaType(uri.toURL());
        Assert.assertTrue(String.format("mpeg_vid.mpg was expected to be a video, but it was instead '%s'.", type), type == MediaType.VIDEO);

        URI uri2 = ioUtils.findFile("/samples/meds1.jpg");
        MediaType type2 = ioUtils.getMediaType(uri2.toURL());
        Assert.assertTrue(String.format("meds1.jpg was expected to be an image, but it was instead '%s'.", type2), type2 == MediaType.IMAGE);

        URI uri3 = ioUtils.findFile("/samples/green.wav");
        MediaType type3 = ioUtils.getMediaType(uri3.toURL());
        Assert.assertTrue(String.format("test.wav was expected to be an audio, but it was instead '%s'.", type3), type3 == MediaType.AUDIO);
    }

    //assert that setting a relative priority on a job effects a measured difference in completion time


}