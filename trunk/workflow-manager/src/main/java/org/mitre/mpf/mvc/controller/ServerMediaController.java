/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.mvc.model.DirectoryTreeNode;
import org.mitre.mpf.mvc.model.ServerMediaFile;
import org.mitre.mpf.mvc.model.ServerMediaFilteredListing;
import org.mitre.mpf.mvc.model.ServerMediaListing;
import org.mitre.mpf.mvc.security.OutgoingRequestTokenService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.service.ServerMediaService;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.ForwardHttpResponseUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;

@Api(value = "Server Media", description = "Server media retrieval")
@RestController
public class ServerMediaController {

    private static final Logger log = LoggerFactory.getLogger(ServerMediaController.class);

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final ServerMediaService _serverMediaService;

    private final JobRequestDao _jobRequestDao;

    private final JsonUtils _jsonUtils;

    private final S3StorageBackend _s3StorageBackend;

    private final HttpClientUtils _httpClient;

    private final OutgoingRequestTokenService _tokenService;

    private final AuditEventLogger _auditEventLogger;

    @Inject
    ServerMediaController(
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            ServerMediaService serverMediaService,
            JobRequestDao jobRequestDao,
            JsonUtils jsonUtils,
            S3StorageBackend s3StorageBackend,
            HttpClientUtils httpClient,
            OutgoingRequestTokenService tokenService,
            AuditEventLogger auditEventLogger) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _serverMediaService = serverMediaService;
        _jobRequestDao = jobRequestDao;
        _jsonUtils = jsonUtils;
        _s3StorageBackend = s3StorageBackend;
        _httpClient = httpClient;
        _tokenService = tokenService;
        _auditEventLogger = auditEventLogger;
    }


    private static class SortAlphabeticalCaseInsensitive implements Comparator<ServerMediaFile> {
        @Override
        public int compare(ServerMediaFile s1, ServerMediaFile s2) {
            return s1.getName().toLowerCase().compareTo(s2.getName().toLowerCase());
        }
    }

    private static class SortAlphabeticalCaseSensitive implements Comparator<ServerMediaFile> {
        @Override
        public int compare(ServerMediaFile s1, ServerMediaFile s2) {
            return s1.getName().compareTo(s2.getName());
        }
    }

    @GetMapping("/server/get-all-directories")
    @RequestEventId(value = LogAuditEventRecord.EventId.GET_DIRECTORY_LISTING)
    public DirectoryTreeNode getAllDirectories(HttpServletRequest request, @RequestParam(required = false) Boolean useUploadRoot,
                                               @RequestParam(required = false, defaultValue = "true") boolean useCache){
        String nodePath = _propertiesUtil.getServerMediaTreeRoot();

        // if useUploadRoot is set it will take precedence over nodeFullPath
        DirectoryTreeNode node = _serverMediaService.getAllDirectories(nodePath, request.getServletContext(), useCache,
                                                                      _propertiesUtil.getRemoteMediaDirectory().getAbsolutePath());
        if(useUploadRoot != null && useUploadRoot){
            node =  DirectoryTreeNode.find(node, _propertiesUtil.getRemoteMediaDirectory().getAbsolutePath());
        }

        return node;
    }

    @GetMapping("/server/get-all-files")
    public ServerMediaListing getAllFiles(HttpServletRequest request, @RequestParam(required = true) String fullPath,
                                          @RequestParam(required = false, defaultValue = "true") boolean useCache) {
        var eventId = LogAuditEventRecord.EventId.GET_DIRECTORY_LISTING;
        File dir = new File(fullPath);
        if (!dir.isDirectory() || !fullPath.startsWith(_propertiesUtil.getServerMediaTreeRoot())) {
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.fail)
                .withUri(request.getRequestURI())
                .error(eventId.message + " failed for path \"" + dir + "\"");
            return null; // security check
        }


        List<ServerMediaFile> mediaFiles = _serverMediaService.getFiles(fullPath, request.getServletContext(), useCache, true);
        _auditEventLogger.readEvent()
            .withSecurityTag()
            .withEventId(eventId.success)
            .withUri(request.getRequestURI())
            .allowed(eventId.message + " succeeded for path \"" + dir + "\"");
        return new ServerMediaListing(mediaFiles);
    }

    //https://datatables.net/manual/server-side#Sent-parameters
    //draw is the counter of how many times it has called back
    //length is how many to return
    //start is offset from 0
    //search is string to filter
    @PostMapping("/server/get-all-files-filtered")
    public ServerMediaFilteredListing getAllFilesFiltered(HttpServletRequest request, @RequestParam(value="fullPath", required=true) String fullPath,
                                                          @RequestParam(required = false, defaultValue = "true") boolean useCache,
                                                          @RequestParam(value="draw", required=false) int draw,
                                                          @RequestParam(value="start", required=false) int start,
                                                          @RequestParam(value="length", required=false) int length,
                                                          @RequestParam(value="search", required=false) String search){
        log.debug("Params fullPath:{} draw:{} start:{} length:{} search:{} ",fullPath, draw, start, length, search);
        var eventId = LogAuditEventRecord.EventId.GET_DIRECTORY_LISTING;
        File dir = new File(fullPath);
        if (!dir.isDirectory() || !fullPath.startsWith(_propertiesUtil.getServerMediaTreeRoot())) {
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.fail)
                .withUri(request.getRequestURI())
                .error(eventId.message + " failed for path \"" + dir + "\"");
            return null; // security check
        }

        List<ServerMediaFile> mediaFiles = _serverMediaService.getFiles(fullPath, request.getServletContext(), useCache, false);

        // handle sort
        Collections.sort(mediaFiles, (new SortAlphabeticalCaseInsensitive()). // make 'A' come before 'B'
                thenComparing(new SortAlphabeticalCaseSensitive()) );         // make 'A' come before 'a'

        // handle search
        if(search != null && search.length() > 0) {
            List<ServerMediaFile> searchResults = new ArrayList<>();
            for (ServerMediaFile mediaFile : mediaFiles) {
                if (mediaFile.getName().toLowerCase().contains(search.toLowerCase())) {
                    searchResults.add(mediaFile);
                }
            }
            mediaFiles = searchResults;
        }

        // handle paging
        int end = start + length;
        end = (end > mediaFiles.size())? mediaFiles.size() : end;
        start = (start <= end)? start : end;

        _auditEventLogger.readEvent()
            .withSecurityTag()
            .withEventId(eventId.success)
            .withUri(request.getRequestURI())
            .allowed(eventId.message + " succeeded for path \"" + dir + "\"");
        return new ServerMediaFilteredListing(draw, mediaFiles.size(), mediaFiles.size(), mediaFiles.subList(start, end));
    }


    @GetMapping("/server/node-image")
    public Object serve(@RequestParam("nodeFullPath") String nodeFullPath,
                        HttpServletRequest request) {
        var eventId = LogAuditEventRecord.EventId.VIEW_MEDIA;
        var dir = new File(nodeFullPath);
        if (!dir.isDirectory() || !nodeFullPath.startsWith(_propertiesUtil.getServerMediaTreeRoot())){
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.fail)
                .withUri(request.getRequestURI())
                .error(eventId.message + " failed for path \"" + dir + "\"");
            return null; // security check
        }
        var path = Paths.get(nodeFullPath);
        if (Files.isReadable(path)) {
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.success)
                .withUri(request.getRequestURI())
                .allowed(eventId.message + " succeeded for path \"" + path + "\"");
            return new PathResource(path);
        }
        else {
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.fail)
                .withUri(request.getRequestURI())
                .error(eventId.message + " failed: path \"" + path + "\" is not readable or does not exist");
            return ResponseEntity.notFound().build();
        }
    }


    @GetMapping("/server/download")
    public Object download(
            @RequestParam("jobId") String jobId,
            @RequestParam("sourceUri") URI sourceUri,
            HttpServletRequest httpRequest) throws StorageException, IOException {
        var eventId = LogAuditEventRecord.EventId.DOWNLOAD_MEDIA;
        var uri = httpRequest.getRequestURI();
        if ("file".equalsIgnoreCase(sourceUri.getScheme())) {
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.success)
                .withUri(uri)
                .allowed(eventId.message + " succeeded for file \"" + sourceUri.toString() + "\"");
            return new PathResource(sourceUri);
        }

        long internalJobId = _propertiesUtil.getJobIdFromExportedId(jobId);
        JobRequest jobRequest = _jobRequestDao.findById(internalJobId);
        if (jobRequest == null) {
            String errString = String.format("Media for job id %s download failed. Invalid job id.", jobId);
            log.error(errString);
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.fail)
                .withUri(uri)
                .error(eventId.message + " failed for file \"" + sourceUri.toString() + "\" : " + errString);
            return ResponseEntity.notFound().build();
        }


        var job = _jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);
        var combinedProperties
                = _aggregateJobPropertiesUtil.getCombinedProperties(job, sourceUri);
        var uriScheme = UriScheme.parse(sourceUri.getScheme());
        if ((uriScheme.equals(UriScheme.HTTP) || uriScheme.equals(UriScheme.HTTPS)) &&
                S3StorageBackend.requiresS3MediaDownload(combinedProperties)) {
            var s3Stream = _s3StorageBackend.getFromS3(sourceUri.toString(), combinedProperties);
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.success)
                .withUri(uri)
                .allowed(eventId.message + " succeeded for file \"" + sourceUri.toString() + "\"");
            return ForwardHttpResponseUtil.createResponseEntity(s3Stream);
        }

        var request = new HttpGet(sourceUri);
        _tokenService.addTokenToRemoteMediaDownloadRequest(job, sourceUri, request);
        var responseToForward = _httpClient.executeRequestSync(request, 0);
        _auditEventLogger.readEvent()
                .withSecurityTag()
                .withEventId(eventId.success)
                .withUri(uri)
                .allowed(eventId.message + " succeeded for file \"" + sourceUri.toString() + "\"");
        return ForwardHttpResponseUtil.createResponseEntity(responseToForward);
    }
}
