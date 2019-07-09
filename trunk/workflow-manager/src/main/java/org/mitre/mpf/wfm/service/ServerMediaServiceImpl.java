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

package org.mitre.mpf.wfm.service;

import org.mitre.mpf.mvc.model.DirectoryTreeNode;
import org.mitre.mpf.mvc.model.ServerMediaFile;
import org.mitre.mpf.mvc.model.ServerMediaListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServerMediaServiceImpl implements ServerMediaService {

    private static final Logger log = LoggerFactory.getLogger(ServerMediaServiceImpl.class);

    @Autowired
    private FileWatcherService fileCacheService;

    public DirectoryTreeNode getAllDirectories() {
        return fileCacheService.getRootDirectoryTreeCache();
    }

    public List<ServerMediaFile> getFiles(String dirPathName) {
        List<ServerMediaFile> mediaFiles = new ArrayList<>();
        Path dirPath = Paths.get(dirPathName).toAbsolutePath();
        ServerMediaListing cachedFileNames = fileCacheService.getFileCache().get(dirPath);
        if (cachedFileNames != null) {
            log.debug("Using cached file listing: " + dirPath);
            mediaFiles.addAll(cachedFileNames.getData());
        } else {
            log.error("Media file cache not found");
        }
        return mediaFiles;
    }

}