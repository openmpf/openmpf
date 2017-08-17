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

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.FeedForwardTopConfidenceCountType;
import org.mitre.mpf.wfm.util.TimePair;
import org.mitre.mpf.wfm.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSegmenting {
	private static final Logger log = LoggerFactory.getLogger(TestSegmenting.class);
	private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

	private TimeUtils timeUtils = new TimeUtils();

	private SortedSet<TimePair> timePairs = new TreeSet<>();
  private TimePair source = new TimePair(0, 15);
  private TimePair target = new TimePair(10, 20);
  private TimePair result = new TimePair(0, 20);

	public TestSegmenting() {
		timePairs.add(new TimePair(0, 24));
	}

	// TODO: add TimeUtils feed-forward tests (i.e. current behavior vs. feed-forward with time pair merging disabled and detection confidence considerations used for construction of segments)

  @Test(timeout = 5 * MINUTES)
  public void TestNoFeedForwardTrackProcessing() throws Exception {
	  // construct unit test for openmpf segmenting (current behavior, no feed-forward processing),
    // Example 1: segments to include frames that don't have missing detections
    SortedSet<Track> testTrackSetEx1 = new TreeSet<>();
    int jobId=1;
    long mediaId=1L;
    int stageIndex=1;
    int actionIndex=1;
    String type="FACE";
    int startOffsetFrameInclusive = 0;
//    int endOffsetFrameInclusive = 29;
    int endOffsetFrameInclusive = 9;
    int targetSegmentLength = 10;
    int minSegmentLength = 10;
    int expectedNumSegments = (int)Math.ceil((endOffsetFrameInclusive-startOffsetFrameInclusive+1)/targetSegmentLength);
    Track testTrackEx1 = new Track(jobId,mediaId,stageIndex,actionIndex,startOffsetFrameInclusive,endOffsetFrameInclusive,type);
    SortedSet<Detection> testDetections = new TreeSet<Detection>();
    SortedMap<String, String> detectionProperties = new TreeMap<String,String>();
    int confidence = 58; // for this test, variation of confidence doesn't matter
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=85+i;
      int offsetFrame=i;
      int offsetTime=i;
      Detection testDetection = new Detection(x,212,offsetFrame,offsetTime,confidence,0,0,detectionProperties);
      testDetections.add(testDetection);
    }
    testTrackEx1.setExemplar(testDetections.first());
    testTrackEx1.setDetections(testDetections);
    Assert.assertTrue("Baseline check ex1 assertion failed, start frame offset is not "+startOffsetFrameInclusive,testTrackEx1.getStartOffsetFrameInclusive()==startOffsetFrameInclusive);
    Assert.assertTrue("Baseline check ex1 assertion failed, end frame offset is not "+endOffsetFrameInclusive,testTrackEx1.getEndOffsetFrameInclusive()==endOffsetFrameInclusive);
    Assert.assertTrue("Baseline check ex1 assertion failed, not "+(endOffsetFrameInclusive+1)+" frames in track",testTrackEx1.getDetections().size()==endOffsetFrameInclusive+1);
    testTrackSetEx1.add(testTrackEx1);

    FeedForwardTopConfidenceCountType feedForwardTopConfidenceCount = FeedForwardTopConfidenceCountType.USE_ALL_DETECTIONS_IN_TRACK;
    List<TimePair> resultsEx1 = timeUtils.createSegments(testTrackSetEx1, feedForwardTopConfidenceCount, targetSegmentLength, minSegmentLength);
    Assert.assertTrue("Baseline check ex1 assertion failed, not "+expectedNumSegments+" segments - number of segments is "+resultsEx1.size(),resultsEx1.size()==expectedNumSegments); // 3 segments should have been returned
    // TODO there might be a bug in method TimeUtils:createTimePairsForTracks, when forming the TimePair for the last segment it looks like TimePair:getEndOffsetFrameInclusive() return a value that is too large (by 1)
    // there should be frames 0-9 in the first segment
    if ( resultsEx1.size() >= 1 ) {
      Assert.assertTrue("Baseline check ex1 assertion failed, first segment start,end indices are: start="+resultsEx1.get(0).getStartInclusive()+
              ", end="+resultsEx1.get(0).getEndInclusive(),
          resultsEx1.get(0).getStartInclusive() == 0 && resultsEx1.get(0).getEndInclusive() == 9);
    }
    // there should be frames 10-19 in the second segment
    if ( resultsEx1.size() >= 2 ) {
      Assert.assertTrue("Baseline check ex1 assertion failed, second segment start,end indices are: start="+resultsEx1.get(1).getStartInclusive()+
              ", end="+resultsEx1.get(1).getEndInclusive(),
          resultsEx1.get(1).getStartInclusive() == 10 && resultsEx1.get(1).getEndInclusive() == 19);
    }
    // there should be frames 20-29 in the third segment
    if ( resultsEx1.size() >= 3 ) {
      Assert.assertTrue("Baseline check ex1 assertion failed, third segment start,end indices are: start="+resultsEx1.get(2).getStartInclusive()+
          ", end="+resultsEx1.get(2).getEndInclusive(),
          resultsEx1.get(2).getStartInclusive() == 20 && resultsEx1.get(2).getEndInclusive() == 29);
    }

    // TODO add unit test for Example 2
    // Example 2: segments to include frames that have missing detections
  }


  @Test(timeout = 5 * MINUTES)
	public void TestNoSplits() throws Exception {
		List<TimePair> results = timeUtils.createSegments(timePairs, 25, 1, 100);
		Assert.assertTrue(results.get(0).getStartInclusive() == 0 && results.get(0).getEndInclusive() == 24);
	}

	@Test(timeout = 5 * MINUTES)
	public void TestSplits() throws Exception {
		List<TimePair> results = timeUtils.createSegments(timePairs, 10, 1, 100);
		Assert.assertTrue(results.get(0).getStartInclusive() == 0 && results.get(0).getEndInclusive() == 9);
		Assert.assertTrue(results.get(1).getStartInclusive() == 10 && results.get(1).getEndInclusive() == 19);
		Assert.assertTrue(results.get(2).getStartInclusive() == 20 && results.get(2).getEndInclusive() == 24);
	}

    @Test(timeout = 1 * MINUTES)
    public void TestOverlap() throws Exception {
        int minGapBetweenSegments = 1;
        Assert.assertTrue(timeUtils.overlaps(source, target, minGapBetweenSegments));
    }

	@Test(timeout = 1 * MINUTES)
    public void TestMerge() throws Exception {
        Assert.assertTrue(timeUtils.merge(source, target).equals(result));
    }
}
