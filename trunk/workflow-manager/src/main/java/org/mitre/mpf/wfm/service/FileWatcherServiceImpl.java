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
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

@Service
public class FileWatcherServiceImpl implements FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherServiceImpl.class);

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private IoUtils ioUtils;

    private final HashMap<WatchKey, Path> watcherMap = new HashMap<>();
    private WatchService watcher;

    private boolean watcherInstantiated = false;

    public void launchWatcher(String nodePath, ServletContext context, String uploadDir) {
        if (!watcherInstantiated) {
            try {
                this.watcher = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                log.error("Failed to get watch service: ");
                throw new UncheckedIOException(e);
            }

            Path cacheFolder = Paths.get(nodePath);
            walkAndRegisterDirectories(cacheFolder);
            Thread thread = new Thread(() -> watcherThreadService(watcher, context));
            thread.setDaemon(true);
            thread.start();
            watcherInstantiated = true;
        }
    }

    private void walkAndRegisterDirectories(final Path start) {
        // register directory and sub-directories
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    registerDirectory(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to register file directory tree under: " + start);
            throw new UncheckedIOException(e);
        }
    }

    private void registerDirectory(Path dir) {
        // register the base media directory with a file watcher service, listening for file creation or deletion
        WatchKey key;
        try {
            key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
        } catch (IOException e) {
            log.error("Failed to register file watcher: " + dir);
            throw new UncheckedIOException(e);
        }
        log.info("File watcher registered for directory: " + dir);
        this.watcherMap.put(key, dir);
    }

    private void watcherThreadService(WatchService watcher, ServletContext context) {
        log.info("Watcher task started");
        try {
            WatchKey key;
            while ((key = watcher.take()) != null) { // blocks until watcher.take() returns an event
                Path dir = watcherMap.get(key);
                if (dir == null) {
                    log.error("WatchKey not recognized!!");
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    //process
                    log.debug("File event occurred in watched directory");
                    Path eventPath = ((WatchEvent<Path>) event).context();
                    Path child = dir.resolve(eventPath);
                    if (event.kind() == ENTRY_CREATE) {
                        File newFile = new File(child.toAbsolutePath().toString());
                        addToCache(newFile, context);
                    } else if (event.kind() == ENTRY_DELETE) {
                        File removedItem = new File(child.toAbsolutePath().toString());
                        removeFromCache(removedItem, context);
                        log.debug("File deleted: " + event.context());
                    } else {
                        log.debug("Unknown event type occurred: " + event.kind() + ", context: " + event.context());
                    }
                }
                key.reset();
            }
            watcher.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error occurred in file watcher: ");
            throw new IllegalStateException(e);
        } catch (IOException e) {
            log.error("Error occurred in file watcher: ");
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Should only be used on startup to load the initial cache
     *
     * @param dirPath
     * @param context
     */
    public void buildInitialCache(String dirPath, ServletContext context) {

        DirectoryTreeNode node = new DirectoryTreeNode(new File(dirPath));
        DirectoryTreeNode.fillDirectoryTree(node, new ArrayList<>(), dirPath);
        node = DirectoryTreeNode.find(node, dirPath);

        File dir = new File(node.getFullPath());

        // attempt to get attribute from application scope
        String attributeName = CACHED_DIRECTORY_STRUCTURE_PREFIX + propertiesUtil.getServerMediaTreeRoot();

        List<ServerMediaFile> mediaFiles = new ArrayList<>();
        prepareFilesForCache(dir, mediaFiles);

        // recurse
        if (node.getNodes() != null) {
            for (DirectoryTreeNode subNode : node.getNodes()) {
                buildInitialCache(subNode.getFullPath(), context);
            }
        }

        // update cache
        context.setAttribute(attributeName, node);
        ServerMediaListing listing = new ServerMediaListing(mediaFiles);
        attributeName = CACHED_FILES_PREFIX + node.getFullPath();
        context.setAttribute(attributeName, listing);
    }

    private void prepareFilesForCache(File item, List<ServerMediaFile> currentFiles) {
        File[] tmpFiles;
        if (item.isDirectory()) {
            tmpFiles = item.listFiles(File::isFile);
        } else {
            tmpFiles = new File[]{item};
        }

        List<File> files = new ArrayList<>(); // use real paths
        List<File> parents = new ArrayList<>(); // use absolute paths
        for (File tmpFile : tmpFiles) {
            try {
                files.add(tmpFile.toPath().toRealPath().toFile()); // resolve symbolic links
                parents.add(tmpFile.getParentFile());
            } catch (IOException e) {
                log.error("Error determining real path: " + e.getMessage(), e);
            }
        }

        // filter by approved list of content type
        List<File> filesFiltered = new ArrayList<>();
        List<File> parentsFiltered = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            // FIXME this will never return false because MediaTypeUtils.parse() never returns null
            if (ioUtils.isApprovedFile(files.get(i))) {
                filesFiltered.add(files.get(i));
                parentsFiltered.add(parents.get(i));
            }
        }
        files = filesFiltered;
        parents = parentsFiltered;

        // build output
        List<ServerMediaFile> mediaFiles = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            // file should have real path, parent should have absolute path
            mediaFiles.add(new ServerMediaFile(files.get(i), parents.get(i),
                    ioUtils.getMimeType(files.get(i).getAbsolutePath())));
        }

        currentFiles.addAll(mediaFiles);
    }

    private void addToCache(File item, ServletContext context) {
        if (item == null) return;

        String attributeName = CACHED_FILES_PREFIX + item.getParentFile().getAbsolutePath();
        // attempt to get attribute from application scope

        // update cache if it exists; else do nothing
        if (context.getAttribute(attributeName) != null) {
            if (item.isDirectory()) {
                context.setAttribute(CACHED_FILES_PREFIX + item.getAbsolutePath(),
                        new ServerMediaListing(new ArrayList<>()));
                walkAndRegisterDirectories(Paths.get(item.getAbsolutePath()));
                log.debug("Directory added to cache: " + item.getAbsolutePath());
            } else {
                ServerMediaListing oldListing = (ServerMediaListing) context.getAttribute(attributeName);
                List<ServerMediaFile> mediaFiles = new ArrayList<>(oldListing.getData());
                prepareFilesForCache(item, mediaFiles);

                // update cache
                ServerMediaListing newListing = new ServerMediaListing(mediaFiles);
                context.setAttribute(attributeName, newListing);
                log.debug("File" + item.getName() + " added to cache: " + attributeName);
            }
            // Update directory tree
            DirectoryTreeNode node = DirectoryTreeNode.fillDirectoryTree((DirectoryTreeNode) context.getAttribute(
                    CACHED_DIRECTORY_STRUCTURE_PREFIX + propertiesUtil.getServerMediaTreeRoot()),
                    new ArrayList<>(), propertiesUtil.getServerMediaTreeRoot());
            context.setAttribute(CACHED_DIRECTORY_STRUCTURE_PREFIX +
                    propertiesUtil.getServerMediaTreeRoot(), node);
        }
    }

    private void removeFromCache(File item, ServletContext context) {
        if (item == null) return;

        // attempt to get attribute from application scope
        String attributeName = CACHED_DIRECTORY_STRUCTURE_PREFIX + propertiesUtil.getServerMediaTreeRoot();

        // update cache if it exists; else do nothing
        if (context.getAttribute(attributeName) != null) {
            // cannot determine file type since it has been deleted
            if (context.getAttribute(CACHED_FILES_PREFIX + item.getAbsolutePath()) != null) {
                // directory
                // The OS will delete files from the lowest level up, therefore we do not need to worry about
                // subdirectories or sub files. This directory is guaranteed to be empty since those deletion events
                // will have cleared everything below it from the cache
                context.removeAttribute(CACHED_FILES_PREFIX + item.getAbsolutePath());
                WatchKey deletedDirKey = null;
                for (WatchKey key : watcherMap.keySet()) {
                    Path path = watcherMap.get(key);
                    if (path.toAbsolutePath().toString().equals(item.getAbsolutePath())) {
                        deletedDirKey = key;
                        break;
                    }
                }
                if (deletedDirKey != null) {
                    deletedDirKey.reset();
                    watcherMap.remove(deletedDirKey);
                }
                log.debug("Directory removed from cache: " + item.getAbsolutePath());
            } else {
                // file
                attributeName = CACHED_FILES_PREFIX + item.getParentFile().getAbsolutePath();
                ServerMediaListing oldListing = (ServerMediaListing) context.getAttribute(attributeName);

                List<ServerMediaFile> mediaFiles = new ArrayList<>();
                for (ServerMediaFile cachedFile : oldListing.getData()) {
                    if (!cachedFile.getFullPath().equals(item.getAbsolutePath())) {
                        mediaFiles.add(cachedFile);
                    }
                }
                if (mediaFiles.size() == oldListing.getData().size()) {
                    log.error("No file removed from cache after delete event");
                }

                // update cache
                ServerMediaListing newListing = new ServerMediaListing(mediaFiles);
                context.setAttribute(attributeName, newListing);
            }
            // update directory tree
            DirectoryTreeNode node = DirectoryTreeNode.fillDirectoryTree((DirectoryTreeNode) context.getAttribute(
                    CACHED_DIRECTORY_STRUCTURE_PREFIX + propertiesUtil.getServerMediaTreeRoot()),
                    new ArrayList<>(), propertiesUtil.getServerMediaTreeRoot());
            context.setAttribute(CACHED_DIRECTORY_STRUCTURE_PREFIX +
                    propertiesUtil.getServerMediaTreeRoot(), node);
            log.debug("File" + item.getName() + " added to cache: " + attributeName);
        }
    }

}
