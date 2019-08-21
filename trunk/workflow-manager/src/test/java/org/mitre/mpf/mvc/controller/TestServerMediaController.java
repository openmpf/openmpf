/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc.controller;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.mvc.model.DirectoryTreeNode;
import org.mitre.mpf.mvc.model.ServerMediaFilteredListing;
import org.mitre.mpf.mvc.model.ServerMediaListing;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDao;
import org.mitre.mpf.wfm.service.*;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class TestServerMediaController {

    private ServerMediaController _controller;

    @Mock
    private PropertiesUtil _mockProperties;

    @Mock
    private IoUtils _mockIoUtils;

    @Mock
    private HibernateJobRequestDao _mockJobRequestDao;

    @Mock
    private JsonUtils _mockJsonUtils;

    @Mock
    private HttpServletRequest _mockRequest;

    @Mock
    private S3StorageBackend _mockS3StorageBackend;

    private static final String _testFileName1 = "test-video.mp4";
    private static final String _testFileName2 = "test-document.pdf";
    private static final String _testFileName3 = "test-image.png";
    private static final int SLEEP_TIME_MILLISEC = 100;
    private ServerMediaService serverMediaService;
    private File mediaBase;

    @Rule
    public TemporaryFolder _rootTempFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder _extTempFolder = new TemporaryFolder();

    @Before
    public void init() throws InterruptedException {
        MockitoAnnotations.initMocks(this);

        mediaBase = _rootTempFolder.getRoot();

        when(_mockProperties.getServerMediaTreeRoot())
                .thenReturn(mediaBase.getAbsolutePath());
        when(_mockProperties.getRemoteMediaDirectory())
                .thenReturn(mediaBase);


        FileWatcherService fileCacheService = new FileWatcherServiceImpl(_mockProperties, _mockIoUtils);
        serverMediaService = new ServerMediaServiceImpl(fileCacheService);
        _controller = new ServerMediaController(_mockProperties, serverMediaService, _mockJobRequestDao,
                _mockJsonUtils, _mockS3StorageBackend);
        fileCacheService.launchWatcher(mediaBase.getAbsolutePath());
        // wait for cache to load
        Thread.sleep(SLEEP_TIME_MILLISEC);
    }

    // get-all-directories

    @Test
    public void getAllDirectoriesJustRoot() {
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(rootNode.getFullPath(), _mockProperties.getServerMediaTreeRoot());
    }

    @Test
    public void getAllDirectoriesNested() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());
    }

    @Test
    public void getAllDirectoriesAfterDeletion() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        Assert.assertTrue(subFolder.delete());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        Assert.assertNull(rootNode.getNodes());
    }

    @Test
    public void getAllDirectoriesAfterEvents() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        File file = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file.createNewFile());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode newTree = _controller.getAllDirectories(_mockRequest).getBody();

        Assert.assertEquals(rootNode, newTree);

        Assert.assertTrue(file.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);
        newTree = _controller.getAllDirectories(_mockRequest).getBody();

        Assert.assertEquals(rootNode, newTree);
    }

    // get-all-files

    @Test
    public void getAllFilesRootFolder() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(SLEEP_TIME_MILLISEC);
        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();

        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName2, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(2).getName());
    }

    @Test
    public void getAllFilesNestedFolderAfterCreation() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertNotNull(rootNode.getNodes());
        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();
        Assert.assertEquals(0, mediaListing.getData().size());

        File file1 = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file1.createNewFile());
        File file2 = new File(subFolder.getAbsolutePath() + "/" + _testFileName2);
        Assert.assertTrue(file2.createNewFile());
        File file3 = new File(subFolder.getAbsolutePath() + "/" + _testFileName3);
        Assert.assertTrue(file3.createNewFile());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        mediaListing = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();

        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName2, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(2).getName());
    }

    @Test
    public void getAllFilesAfterDeletion() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(SLEEP_TIME_MILLISEC);
        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();

        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName2, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(2).getName());

        Assert.assertTrue(file.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);
        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();

        assertEquals(2, mediaListing.getData().size());
        assertEquals(_testFileName2, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(1).getName());
    }

    @Test
    public void getFilesFromDeletedDirectory() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        File file = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file.createNewFile());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());

        Assert.assertTrue(file.delete());
        Assert.assertTrue(subFolder.delete());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        rootNode = _controller.getAllDirectories(_mockRequest).getBody();
        Assert.assertNull(rootNode.getNodes());

        ServerMediaListing fileList = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();
        Assert.assertNull(fileList);
    }

    @Test
    public void getFilesFromInvalidDirectory() {
        HttpStatus response = _controller.getAllFiles(_mockRequest, _mockProperties.getServerMediaTreeRoot() + "/lskjdflksjf").getStatusCode();
        Assert.assertEquals(HttpStatus.NOT_FOUND, response);

        response = _controller.getAllFiles(_mockRequest, "/lskjdflksjf").getStatusCode();
        Assert.assertEquals(HttpStatus.BAD_REQUEST, response);

        response = _controller.getAllFiles(_mockRequest, "/tmp").getStatusCode();
        Assert.assertEquals(HttpStatus.BAD_REQUEST, response);
    }

    // get-all-files-filtered

    @Test
    public void getFilesSorted() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(SLEEP_TIME_MILLISEC);
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                mediaBase.getAbsolutePath(), 1 , 0, 10, "");

        ServerMediaListing mediaListing = mediaListingResult.getBody();
        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName2, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName1, mediaListing.getData().get(2).getName());
    }

    @Test
    public void getFilesFiltered() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(SLEEP_TIME_MILLISEC);
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                mediaBase.getAbsolutePath(), 1 , 0, 10, "mp4");

        ServerMediaListing mediaListing = mediaListingResult.getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
    }

    @Test
    public void getFilesFilteredInvalidDirectory() {
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                "/tmp", 1 , 0, 10, "");
        assertSame(HttpStatus.BAD_REQUEST, mediaListingResult.getStatusCode());

        mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                "/lskjdflksjf", 1 , 0, 10, "");
        assertSame(HttpStatus.BAD_REQUEST, mediaListingResult.getStatusCode());

        mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                _mockProperties.getServerMediaTreeRoot() + "/slfkjasdlksdf", 1 , 0, 10, "");
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());
    }

    @Test
    public void getFilesFilteredDeletedDirectory() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");
        Thread.sleep(SLEEP_TIME_MILLISEC);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        File file = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file.createNewFile());
        Assert.assertTrue(file.exists());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                subFolder.getAbsolutePath(), 1 , 0, 10, "");
        ServerMediaListing mediaListing = mediaListingResult.getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());

        Assert.assertTrue(file.delete());
        Assert.assertTrue(subFolder.delete());

        Thread.sleep(SLEEP_TIME_MILLISEC);
        rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertNull(rootNode.getNodes());

        mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                subFolder.getAbsolutePath(), 1 , 0, 10, "");
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());
    }


    private void createAndRemoveSymlink() throws IOException, InterruptedException {
        File extTestFile = _extTempFolder.newFile("test-text.txt");

        // create symlink

        Path target = _extTempFolder.getRoot().toPath();
        Path link = _rootTempFolder.getRoot().toPath().resolve("link");

        Files.createSymbolicLink(link, target);
        assertTrue(Files.isSymbolicLink(link));
        Thread.sleep(SLEEP_TIME_MILLISEC);

        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, link.toFile().getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(extTestFile.getName(), mediaListing.getData().get(0).getName());

        // remove file in symlink dir

        assertTrue(extTestFile.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, link.toFile().getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        // remove symlink

        assertTrue(link.toFile().delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        ResponseEntity<ServerMediaListing> mediaListingResult = _controller.getAllFiles(_mockRequest,
                link.toFile().getAbsolutePath());
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());
    }

    @Test
    public void recreateSymlink() throws IOException, InterruptedException {
        // root
        // + link --> ext
        // ext
        // + text-text.txt
        createAndRemoveSymlink(); // pass 0
        createAndRemoveSymlink(); // pass 1
    }


    private void createAndRemoveTwinSymlinks(String linkASubPath, String linkBSubPath) throws IOException, InterruptedException {
        File extTestFile = _extTempFolder.newFile("test-text.txt");

        // create symlinks

        Path target = _extTempFolder.getRoot().toPath();
        Path linkA = _rootTempFolder.getRoot().toPath().resolve(linkASubPath);
        Path linkB = _rootTempFolder.getRoot().toPath().resolve(linkBSubPath);

        Files.createDirectories(linkA.getParent());
        Files.createSymbolicLink(linkA, target);
        assertTrue(Files.isSymbolicLink(linkA));

        Files.createDirectories(linkB.getParent());
        Files.createSymbolicLink(linkB, target);
        assertTrue(Files.isSymbolicLink(linkB));

        Thread.sleep(SLEEP_TIME_MILLISEC);

        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, linkA.toFile().getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(extTestFile.getName(), mediaListing.getData().get(0).getName());

        mediaListing = _controller.getAllFiles(_mockRequest, linkB.toFile().getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(extTestFile.getName(), mediaListing.getData().get(0).getName());

        // remove symlink A

        assertTrue(linkA.toFile().delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        ResponseEntity<ServerMediaListing> mediaListingResult = _controller.getAllFiles(_mockRequest,
                linkA.toFile().getAbsolutePath());
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());

        mediaListing = _controller.getAllFiles(_mockRequest, linkB.toFile().getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(extTestFile.getName(), mediaListing.getData().get(0).getName());

        // remove file in symlink dir

        assertTrue(extTestFile.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, linkB.toFile().getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        // remove symlink B

        assertTrue(linkB.toFile().delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListingResult = _controller.getAllFiles(_mockRequest,
                linkB.toFile().getAbsolutePath());
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());
    }

    @Test
    public void recreateTwinSymlinks() throws IOException, InterruptedException {
        // root
        // + linkA --> ext
        // + linkB --> ext
        // ext
        // + text-text.txt
        createAndRemoveTwinSymlinks("linkA", "linkB"); // pass 0
        createAndRemoveTwinSymlinks("linkA", "linkB"); // pass 1
    }

    @Test
    public void recreateTwinNestedSymlinks() throws IOException, InterruptedException {
        // root
        // + linkA --> ext
        // + nested
        //   + linkB --> ext
        // ext
        // + text-text.txt
        createAndRemoveTwinSymlinks("linkA", "nested/linkB"); // pass 0

        // root
        // + nested
        //   + linkA --> ext
        //   + linkB --> ext
        // ext
        // + text-text.txt
        createAndRemoveTwinSymlinks("nested/linkA", "nested/linkB"); // pass 1
    }


    private void createAndRemoveSymlinkChain() throws IOException, InterruptedException {
        File rootTestFile = _rootTempFolder.newFile("test-text.txt");

        File nestedADir = _rootTempFolder.newFolder("nestedA");
        File nestedATestFile = _rootTempFolder.newFile("nestedA/test-text.txt");

        File nestedBDir = _rootTempFolder.newFolder("nestedB");
        File nestedBTestFile = _rootTempFolder.newFile("nestedB/test-text.txt");

        // create symlinks

        Path linkA = _rootTempFolder.getRoot().toPath().resolve("nestedA/linkA");

        Files.createDirectories(linkA.getParent());
        Files.createSymbolicLink(linkA, rootTestFile.toPath().getParent());
        assertTrue(Files.isSymbolicLink(linkA));

        Path linkB = _rootTempFolder.getRoot().toPath().resolve("nestedB/linkB");

        Files.createDirectories(linkB.getParent());
        Files.createSymbolicLink(linkB, linkA.getParent());
        assertTrue(Files.isSymbolicLink(linkB));

        Thread.sleep(SLEEP_TIME_MILLISEC);

        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedADir.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedBDir.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, linkA.toFile().getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size()); // symlink omitted from cache because it points to existing dir in media base

        mediaListing = _controller.getAllFiles(_mockRequest, linkB.toFile().getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size()); // symlink omitted from cache because it points to existing dir in media base

        // remove file in symlink A dir

        assertTrue(nestedATestFile.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedADir.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedBDir.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        // remove symlink A

        assertTrue(linkA.toFile().delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedADir.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        // remove symlink B

        assertTrue(linkB.toFile().delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedBDir.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        // remove file in symlink B dir

        assertTrue(nestedBTestFile.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());

        mediaListing = _controller.getAllFiles(_mockRequest, nestedBDir.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());

        // remove nested dirs

        nestedADir.delete();
        nestedBDir.delete();
        Thread.sleep(SLEEP_TIME_MILLISEC);

        ResponseEntity<ServerMediaListing> mediaListingResult = _controller.getAllFiles(_mockRequest,
                nestedADir.getAbsolutePath());
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());

        mediaListingResult = _controller.getAllFiles(_mockRequest,
                nestedBDir.getAbsolutePath());
        assertSame(HttpStatus.NOT_FOUND, mediaListingResult.getStatusCode());

        // remove file in root dir

        assertTrue(rootTestFile.delete());
        Thread.sleep(SLEEP_TIME_MILLISEC);

        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
        assertEquals(0, mediaListing.getData().size());
    }

    @Test
    public void createSymlinkChain() throws IOException, InterruptedException {
        // root
        // + text-text.txt
        // + nestedA
        //   + text-test.txt
        //   + linkA --> root
        // + nestedB
        //   + text-test.txt
        //   + linkB --> nestedA
        createAndRemoveSymlinkChain(); // pass 0
        createAndRemoveSymlinkChain(); // pass 1
    }
}
