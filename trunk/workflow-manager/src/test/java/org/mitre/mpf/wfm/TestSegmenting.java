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
import org.junit.runners.MethodSorters;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.TimePair;
import org.mitre.mpf.wfm.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


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

  /** TimeUtils feed-forward test. Note that
   * this tests makes use of the feed-forward appropriate createSegments methods of TimeUtils, which is necessary since for feed-forward TimePair merging
   * should be disabled and detection confidence must be considered during construction of segments.
   * @throws Exception
   */
  @Test(timeout = 5 * MINUTES)
  public void TestFeedForwardSegmentProcessing() throws Exception {
    // Unit tests for openmpf segmenting feed-forward behavior (3 Examples)

    // Example 1: segments to include frames that don't have missing detections,  Frames containing detections with top confidence are first in the track
    SortedSet<Track> testTrackSet = new TreeSet<>();
    int jobId=1;
    long mediaId=1L;
    int stageIndex=1;
    int actionIndex=1;
    String type="FACE";
    int startOffsetFrameInclusive = 0;
    int endOffsetFrameInclusive = 29;
    int targetSegmentLength = 10;
    int minSegmentLength = 1;
    Track testTrack = new Track(jobId,mediaId,stageIndex,actionIndex,startOffsetFrameInclusive,endOffsetFrameInclusive,type);
    SortedMap<String, String> detectionProperties = new TreeMap<String,String>();
    Integer topConfidenceCount = 10; // specify that 10 of the top confidence detections should be used for feed-forward
    int confidence; // for this test, variation of confidence does matter.  In Example 1, the top confidence detections are in the first <targetSegmentLength> frames
    SortedSet<Detection> testDetections = new TreeSet<Detection>();
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=i;
      int y=i;
      int offsetFrame=i;
      int offsetTime=i;
      if ( i < targetSegmentLength ) {
        confidence = 60 + i; // high confidence, increasing with the number of detections for the first <targetSegmentLength> frames
      } else {
        confidence = i < 30 ? 30 - i : 0; // low confidence, decreasing with the number of detections for the remainder of the frames
      }
      // add the detection to the frame, in Example 1 all frames have a detection, and the top confidence <topConfidenceCount> detections are the first <topConfidenceCount> detections
      Detection testDetection = new Detection(x,y,offsetFrame,offsetTime,confidence,i,i,detectionProperties);
      if ( i == (targetSegmentLength-1) ) {
        // save the Detection at index targetSegmentLength as the Exemplar
        testTrack.setExemplar(testDetection);
      }
      testDetections.add(testDetection);
    }

    // store the detections in the Track
    testTrack.getDetections().addAll(testDetections);

    // run preliminary tests
    Assert.assertTrue("FeedForward check Ex1 assertion failed, start frame offset is not "+startOffsetFrameInclusive,testTrack.getStartOffsetFrameInclusive()==startOffsetFrameInclusive);
    Assert.assertTrue("FeedForward check Ex1 assertion failed, end frame offset is not "+endOffsetFrameInclusive,testTrack.getEndOffsetFrameInclusive()==endOffsetFrameInclusive);
    Assert.assertTrue("FeedForward check Ex1 assertion failed, not "+(endOffsetFrameInclusive+1)+" frames in track",testTrack.getDetections().size()==endOffsetFrameInclusive+1);

    // form collection of start and stop times for each track.
    // Note that the feed-forward enabled version of TimeUtils.createSegments should be used here. In addition to accepting a set of Tracks and topConfidenceCount
    // this version of the method will not merge over TimePairs
    testTrackSet.add(testTrack);
    List<TimePair> resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);

    int expectedNumSegments = 1; // for this test, the top <topConfidenceCount> == <targetSegmentLength}, we expect to only get 1 segment
    Assert.assertTrue("FeedForward check Ex1 assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);

    // there should be frames 0-9 in the single segment,
    for ( int i=0; i<resultsEx.size(); i++ ) {
      TimePair timePair = resultsEx.get(i);
      int expectedStartIndex = i*targetSegmentLength;
      int expectedEndIndex = expectedStartIndex + targetSegmentLength - 1;
      Assert.assertTrue("FeedForward check Ex1 assertion failed, segment number "+i+" timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Example 1a, test with topConfidenceCount=1 (use the exemplar), should still return 1 segment but of duration from the exemplar
    // which is the last Detection in our implementation of Example 1
    topConfidenceCount = 1;
    int expectedStartIndex = targetSegmentLength-1;
    int expectedEndIndex = expectedStartIndex;
    resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);
    Assert.assertTrue("FeedForward check Ex1a assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);
    // there should be only the last frame in the single segment,
    for ( int i=0; i<resultsEx.size(); i++ ) {
      TimePair timePair = resultsEx.get(i);
      Assert.assertTrue("FeedForward check Ex1a assertion failed, segment number "+i+" timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Example 1b, test with topConfidenceCount=0 (use all detections).  Result should be the same as NoFeedForward Example 1
    topConfidenceCount = 0;
    expectedNumSegments = 3;
    resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);
    Assert.assertTrue("FeedForward check Ex1b assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);
    // there should be only the last frame in the single segment,
    for ( int i=0; i<resultsEx.size(); i++ ) {
      TimePair timePair = resultsEx.get(i);
      expectedStartIndex = i*targetSegmentLength;
      expectedEndIndex = expectedStartIndex + targetSegmentLength - 1;
      Assert.assertTrue("FeedForward check Ex1b assertion failed, segment number "+i+" timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Example 1c, move top detections to middle of detection set, starting at Detection offset 8 (less than targetSegmentLength).
    topConfidenceCount = 10; // specify that 10 of the top confidence detections should be used for feed-forward in this test
    testDetections = new TreeSet<Detection>();
    boolean isRemoved = testTrackSet.remove(testTrack); // remove the testTrack placed for the last set of tests so we can re-add it to run this test.
    Assert.assertTrue("FeedForward check Ex1c assertion failed, removal of the testTrack from the testTrackSet failed",isRemoved);
    testTrack.getDetections().clear();
    int detectionShift = 8;
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=i;
      int y=1000+i;
      int offsetFrame=i;
      int offsetTime=i;
      if ( i >= (detectionShift-1) && i < (detectionShift+targetSegmentLength-1) ) {
        confidence = 60 + i; // high confidence, increasing with the number of detections for the first <targetSegmentLength> frames
      } else {
        confidence = i < 30 ? 30 - i : 0; // low confidence, decreasing with the number of detections for the remainder of the frames
      }
      // add the detection to the frame, in Example 1c all frames have a detection, and the top confidence <topConfidenceCount> detections are the 8-18 <topConfidenceCount> detections
      Detection testDetection = new Detection(x,y,offsetFrame,offsetTime,confidence,i,i,detectionProperties);
      if ( i == (detectionShift+targetSegmentLength-1) ) {
        // save the Detection at index 8+targetSegmentLength as the Exemplar
        testTrack.setExemplar(testDetection);
      }
      testDetections.add(testDetection);
    }

    // store the detections in the Track
    testTrack.getDetections().addAll(testDetections);

    // form collection of start and stop times for each track using the feed-forward enabled version of TimeUtils.createSegments.
    testTrackSet.add(testTrack);
    resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);

    expectedNumSegments = 1; // for this test, the top <topConfidenceCount> == <targetSegmentLength}, we expect to only get 1 segment
    Assert.assertTrue("FeedForward check Ex1c assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);

    // there should be frames 7-16 in the single segment,
    expectedStartIndex = detectionShift-1;
    expectedEndIndex = expectedStartIndex + targetSegmentLength - 1;
    if ( resultsEx.size() == 1) {
      TimePair timePair = resultsEx.get(0);
      Assert.assertTrue("FeedForward check Ex1c assertion failed, for single segment timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Test Ex1d, where targetSegmentLength>1 and targetSegmentLength>topConfidenceCount
    targetSegmentLength = 9;
    topConfidenceCount = 3;
    // form collection of start and stop times for each track using the feed-forward enabled version of TimeUtils.createSegments.
    testTrackSet.add(testTrack);
    resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);

    expectedNumSegments = 1; // for this test, we expect to only get 1 segment
    Assert.assertTrue("FeedForward check Ex1d assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);

    // since Detection confidence is increasing in Detections (detectionShift-1):(detectionShift-1+targetSegmentLength),
    // there should be frames (detectionShift+(targetSegmentLength-topConfidenceCount)):(detectionShift-1+targetSegmentLength) in the single segment for this test
    expectedStartIndex = detectionShift+(targetSegmentLength-topConfidenceCount);
    expectedEndIndex = detectionShift+targetSegmentLength-1;
    if ( resultsEx.size() == 1 ) {
      TimePair timePair = resultsEx.get(0);
      Assert.assertTrue("FeedForward check Ex1d assertion failed, for single segment with timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Example 2: segments to include frames that don't have missing detections,  Frames containing detections with top confidence are evenly dispersed within the track
    targetSegmentLength = 10;
    topConfidenceCount = 10;
    testTrackSet = new TreeSet<>();
    jobId = 2;
    mediaId = 2L;
    testTrack = new Track(jobId,mediaId,stageIndex,actionIndex,startOffsetFrameInclusive,endOffsetFrameInclusive,type);
    testDetections = new TreeSet<Detection>();
    // For this test, the high confidence detections will be in frames 0,3,6,9,12,15,18,21,24 and 27 i.e. (i%3)=0
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=i;
      int y=i;
      int offsetFrame=i;
      int offsetTime=i;
      if ( (i % 3) == 0 ) {
        confidence = 40; // high confidence detection at first, then at every 3rd detection
      } else {
        confidence = 30; // low confidence detection otherwise
      }
      // add the detection to the frame, in Example 1 all frames have a detection, and the top confidence <topConfidenceCount> detections are the first <topConfidenceCount> detections
      Detection testDetection = new Detection(x,y,offsetFrame,offsetTime,confidence,i,i,detectionProperties);
      if ( i == startOffsetFrameInclusive ) {
        // save the first Detection as the Exemplar
        testTrack.setExemplar(testDetection);
      }
      testDetections.add(testDetection);
    }
    // set set of detections in the test track
    testTrack.getDetections().addAll(testDetections);
    testTrackSet.add(testTrack);

    resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);

    // for this test, the top <topConfidenceCount> == <targetSegmentLength}, we expect to only get 1 segment but the length of that segment should be > targetSegmentLength
    // since the lower confidence detections are effectively ignored.  The segment should be built up until the segment contains the <topConfidenceCount> top detections
    expectedNumSegments = 1;
    Assert.assertTrue("FeedForward check Ex2 assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);
    // there should be number of frames > targetSegmentLength in the single segment and the segment should be defined for frames 0-27
    expectedStartIndex = 0;
    expectedEndIndex = 27;
    for ( int i=0; i<resultsEx.size(); i++ ) {
      TimePair timePair = resultsEx.get(i);
      Assert.assertTrue("FeedForward check Ex2 assertion failed, segment number "+i+" timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Example 3: segments to include frames that don't have missing detections,  Frames containing detections with top confidence are randomly dispersed within the track.
    // topConfidenceCount > targetSegmentLength
    testTrackSet = new TreeSet<>();
    jobId = 3;
    mediaId = 3L;
    testTrack = new Track(jobId,mediaId,stageIndex,actionIndex,startOffsetFrameInclusive,endOffsetFrameInclusive,type);
    testDetections = new TreeSet<Detection>();
    // For this test, the low confidence detections will be in frames 2,5,6,11,13,15,19,22,25 and 26
    int[] lowConfidenceIndices = {2,5,8,11,13,15,19,22,25,26};
    TreeSet <Integer> frameIndexLowConfidenceSet = IntStream.of(lowConfidenceIndices).boxed()
        .collect(Collectors.toCollection(TreeSet::new));
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=i;
      int y=i;
      int offsetFrame=i;
      int offsetTime=i;
      if ( frameIndexLowConfidenceSet.contains(i) ) {
        confidence = 10;      // low confidence detection
      } else {
        confidence = 90;     // high confidence detection otherwise
      }
      // add the detection to the frame, in Example 1 all frames have a detection, and the top confidence <topConfidenceCount> detections are the first <topConfidenceCount> detections
      Detection testDetection = new Detection(x,y,offsetFrame,offsetTime,confidence,i,i,detectionProperties);
      if ( i == startOffsetFrameInclusive ) {
        // save the first Detection as the Exemplar
        testTrack.setExemplar(testDetection);
      }
      testDetections.add(testDetection);
    }
    // save set of detections in the test track
    testTrack.getDetections().addAll(testDetections);
    testTrackSet.add(testTrack);

    topConfidenceCount = 20;
    resultsEx = timeUtils.createSegments(topConfidenceCount, testTrackSet, targetSegmentLength, minSegmentLength);

    // for this test, the top <topConfidenceCount> > <targetSegmentLength}, we expect to get 2 segments but the length of the 1st segment should be > targetSegmentLength
    // since the lower confidence detections are effectively ignored.  The 1st segment should be built up until the segment contains the <topConfidenceCount> top detections
    expectedNumSegments = 2;

    Assert.assertTrue("FeedForward check Ex3 assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx.size()+" segments",resultsEx.size()==expectedNumSegments);
    // there should be 2 segments, with 1st segment containing frames 0-14 and the second segment containing frames 16-29
    if ( resultsEx.size() >= 1 ) {
      TimePair timePair = resultsEx.get(0);
      Assert.assertTrue("FeedForward check Ex3 assertion failed, 1st segment timePair="+timePair,timePair.getStartInclusive() == 0 && timePair.getEndInclusive() == 14);
    }

    if ( resultsEx.size() >= 2 ) {
      TimePair timePair = resultsEx.get(1);
      Assert.assertTrue("FeedForward check Ex3 assertion failed, 2nd segment timePair="+timePair,timePair.getStartInclusive() == 16 && timePair.getEndInclusive() == 29);
    }

  }

  /** TimeUtils feed-forward disabled test. This check tests OpenMPF behavior without feed forward processing.  Note that
   * this tests makes use of the feed-forward appropriate createSegments methods of TimeUtils, which is necessary since for feed-forward TimePair merging
   * should be disabled and detection confidence must be considered during construction of segments.
   * @throws Exception
   */
  @Test(timeout = 5 * MINUTES)
  public void TestNoFeedForwardSegmentProcessing() throws Exception {
	  // Unit tests for openmpf segmenting using current behavior (2 Examples)

    // Example 1: segments to include frames that don't have missing detections,
    SortedSet<Track> testTrackSetEx1 = new TreeSet<>();
    int jobId=1;
    long mediaId=1L;
    int stageIndex=1;
    int actionIndex=1;
    String type="FACE";
    int startOffsetFrameInclusive = 0;
    int endOffsetFrameInclusive = 29;
    int targetSegmentLength = 10;
    int minSegmentLength = 1;
    int minGapBetweenSegments = 0; // is minGapBetweenSegments used appropriately?
    int expectedNumSegments = (int)Math.ceil((endOffsetFrameInclusive-startOffsetFrameInclusive+1)/targetSegmentLength);
    Track testTrackEx1 = new Track(jobId,mediaId,stageIndex,actionIndex,startOffsetFrameInclusive,endOffsetFrameInclusive,type);
    SortedSet<Detection> testDetectionsEx1 = new TreeSet<Detection>();
    SortedMap<String, String> detectionProperties = new TreeMap<String,String>();
    int confidence = 58; // for the no-feed-forward test, variation of detection confidence doesn't matter
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=85+i;
      int offsetFrame=i;
      int offsetTime=i;
      // add the detection to the frame, in Example 1 all frames have a detection
      Detection testDetection = new Detection(x,212,offsetFrame,offsetTime,confidence,i,i,detectionProperties);
      testDetectionsEx1.add(testDetection);
    }
    testTrackEx1.setExemplar(testDetectionsEx1.first());
    testTrackEx1.getDetections().addAll(testDetectionsEx1);
    Assert.assertTrue("NoFeedForward check Ex1 assertion failed, start frame offset is not "+startOffsetFrameInclusive,testTrackEx1.getStartOffsetFrameInclusive()==startOffsetFrameInclusive);
    Assert.assertTrue("NoFeedForward check Ex1 assertion failed, end frame offset is not "+endOffsetFrameInclusive,testTrackEx1.getEndOffsetFrameInclusive()==endOffsetFrameInclusive);
    Assert.assertTrue("NoFeedForward check Ex1 assertion failed, not "+(endOffsetFrameInclusive+1)+" frames in track",testTrackEx1.getDetections().size()==endOffsetFrameInclusive+1);
    testTrackSetEx1.add(testTrackEx1);

    // form collection of start and stop times for each track.  Note that the feed-forward disabled version of TimeUtils.createSegments should be used here (i.e. may do some merging)
    List<TimePair> timePairs = timeUtils.createTimePairsForTracks(testTrackSetEx1);
    List<TimePair> resultsEx1 = timeUtils.createSegments(timePairs, targetSegmentLength, minSegmentLength, minGapBetweenSegments);

    Assert.assertTrue("NoFeedForward check Ex1 assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx1.size()+" segments",resultsEx1.size()==expectedNumSegments); // 3 segments should have been returned
     // there should be frames 0-9 in the first segment, 10-19 in the second segment, etc.
    for ( int i=0; i<resultsEx1.size(); i++ ) {
      TimePair timePair = resultsEx1.get(i);
      int expectedStartIndex = i*targetSegmentLength;
      int expectedEndIndex = expectedStartIndex + targetSegmentLength - 1;
      Assert.assertTrue("NoFeedForward check Ex1 assertion failed, segment number "+i+" timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

    // Example 2: segments to include frames that don't contain detections,
    SortedSet<Track> testTrackSetEx2 = new TreeSet<>();
    jobId=2;
    mediaId=2L;

    Track testTrackEx2 = new Track(jobId,mediaId,stageIndex,actionIndex,startOffsetFrameInclusive,endOffsetFrameInclusive,type);
    SortedSet<Detection> testDetectionsEx2 = new TreeSet<Detection>();
    // from openmpf current behavior Example 2, frames with index 3,4,7,10,11,13 and 15 don't have detections.
    SortedSet<Integer> exclusionIndicesEx2 = new TreeSet<Integer>();
    exclusionIndicesEx2.add(Integer.valueOf(3));
    exclusionIndicesEx2.add(Integer.valueOf(4));
    exclusionIndicesEx2.add(Integer.valueOf(7));
    exclusionIndicesEx2.add(Integer.valueOf(10));
    exclusionIndicesEx2.add(Integer.valueOf(11));
    exclusionIndicesEx2.add(Integer.valueOf(13));
    exclusionIndicesEx2.add(Integer.valueOf(15));
    for ( int i=startOffsetFrameInclusive; i<=endOffsetFrameInclusive; i++ ) {
      int x=85+i;
      int offsetFrame=i;
      int offsetTime=i;
      // add the detection to the frame if that frame index is not in the exclusion list
      if ( !exclusionIndicesEx2.contains(Integer.valueOf(i)) ) {
        Detection testDetection = new Detection(x, 212, offsetFrame, offsetTime, confidence, i, i,
            detectionProperties);
        testDetectionsEx2.add(testDetection);
      }
    }
    testTrackEx2.setExemplar(testDetectionsEx2.first());
    testTrackEx2.getDetections().addAll(testDetectionsEx2);
    Assert.assertTrue("NoFeedForward check Ex2 assertion failed, start frame offset is not "+startOffsetFrameInclusive,testTrackEx2.getStartOffsetFrameInclusive()==startOffsetFrameInclusive);
    Assert.assertTrue("NoFeedForward check Ex2 assertion failed, end frame offset is not "+endOffsetFrameInclusive,testTrackEx2.getEndOffsetFrameInclusive()==endOffsetFrameInclusive);
    // small adjustment for Example2, need to account for the missing detections in the frame count
    Assert.assertTrue("NoFeedForward check Ex2 assertion failed, not "+(endOffsetFrameInclusive+1)+" frames in track",(exclusionIndicesEx2.size()+testTrackEx2.getDetections().size())==endOffsetFrameInclusive+1);
    testTrackSetEx2.add(testTrackEx2);

    // form collection of start and stop times for each track.  Note that the feed-forward disabled version of TimeUtils.createSegments should be used here (i.e. may do some merging)
    timePairs = timeUtils.createTimePairsForTracks(testTrackSetEx1);
    List<TimePair> resultsEx2 = timeUtils.createSegments(timePairs, targetSegmentLength, minSegmentLength, minGapBetweenSegments);

    Assert.assertTrue("NoFeedForward check Ex2 assertion failed, expected "+expectedNumSegments+" segments, but got "+resultsEx2.size()+" segments",resultsEx2.size()==expectedNumSegments); // 3 segments should have been returned
    // there should be frames 0-9 in the first segment, 10-19 in the second segment, etc. i.e. same as in Ex1
    for ( int i=0; i<resultsEx2.size(); i++ ) {
      TimePair timePair = resultsEx2.get(i);
      int expectedStartIndex = i*targetSegmentLength;
      int expectedEndIndex = expectedStartIndex + targetSegmentLength - 1;
      Assert.assertTrue("NoFeedForward check Ex2 assertion failed, segment number "+i+" timePair="+timePair,timePair.getStartInclusive() == expectedStartIndex && timePair.getEndInclusive() == expectedEndIndex);
    }

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
