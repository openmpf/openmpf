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
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
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
import java.util.Map;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

@Service
@Singleton
public class FileWatcherServiceImpl implements FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherServiceImpl.class);

    // Injected
    private PropertiesUtil propertiesUtil;
    private IoUtils ioUtils;

    private Map<Path, ServerMediaListing> fileCache = new HashMap<>();
    private DirectoryTreeNode rootDirectoryTreeCache;

    private boolean watcherInstantiated = false;

    @Inject
    public FileWatcherServiceImpl(PropertiesUtil propertiesUtil, IoUtils ioUtils) {
        this.propertiesUtil = propertiesUtil;
        this.ioUtils = ioUtils;

        File mediaRoot = propertiesUtil.getRemoteMediaDirectory();
        rootDirectoryTreeCache = new DirectoryTreeNode(mediaRoot);
        rootDirectoryTreeCache.fillDirectoryTree(rootDirectoryTreeCache, new ArrayList<>(),
                propertiesUtil.getServerMediaTreeRoot());
    }

    public ServerMediaListing getFileListByPath(Path path) {
        return fileCache.get(path);
    }

    public DirectoryTreeNode getRootDirectoryTreeCache() {
        return rootDirectoryTreeCache;
    }

    public void launchWatcher(String nodePath) {
        if (!watcherInstantiated) {
            Path cacheFolder = Paths.get(nodePath);
            Thread thread = new Thread(() -> watcherThreadService(cacheFolder));
            thread.setDaemon(true);
            thread.start();
            watcherInstantiated = true;
        }
    }

    private void walkAndRegisterDirectories(final Path start, WatchService watcher, Map<WatchKey, Path> watcherMap) {
        // register directory and sub-directories
        try {
            SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    registerDirectory(dir, watcher, watcherMap);
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(start, Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
        } catch (IOException e) {
            log.error("Failed to register file directory tree under: " + start);
            throw new UncheckedIOException(e);
        }
    }

    private void registerDirectory(Path dir, WatchService watcher, Map<WatchKey, Path> watcherMap) {
        // register the current media directory with a file watcher service, listening for file creation or deletion
        WatchKey key;
        try {
            key = dir.toRealPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE);
            log.info("File watcher registered for directory: " + dir);
            watcherMap.put(key, dir);
        } catch (IOException e) {
            log.error("Failed to register file watcher: " + dir);
            throw new UncheckedIOException(e);
        }
    }

    private void watcherThreadService(Path cacheFolder) {
        // Initialize watcher and cache
        WatchService watcher;
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("Failed to get watch service: ");
            throw new UncheckedIOException(e);
        }
        Map<WatchKey, Path> watcherMap = new HashMap<>();

        // Initialize caches
        walkAndRegisterDirectories(cacheFolder, watcher, watcherMap);
        // Load existing files on startup since we won't see their creation event
        buildInitialCache(propertiesUtil.getServerMediaTreeRoot());

        log.info("File watcher task started");
        fileEventLoop(watcher, watcherMap);
    }

    private void fileEventLoop(WatchService watcher, Map<WatchKey, Path> watcherMap) {
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
                        File newItem = new File(child.toAbsolutePath().toString());
                        addToCache(newItem, watcher, watcherMap);
                    } else if (event.kind() == ENTRY_DELETE) {
                        File removedItem = new File(child.toAbsolutePath().toString());
                        removeFromCache(removedItem, watcherMap);
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
     */
    private void buildInitialCache(String dirPath) {
        log.debug("Building initial cache");

        DirectoryTreeNode node = this.rootDirectoryTreeCache;
        node = DirectoryTreeNode.find(node, dirPath);

        File dir = new File(node.getFullPath());

        List<ServerMediaFile> mediaFiles = new ArrayList<>();
        prepareFilesForCache(dir, mediaFiles);

        // update file cache
        ServerMediaListing listing = new ServerMediaListing(mediaFiles);
        fileCache.put(Paths.get(node.getFullPath()), listing);

        // go through nested folders
        if (node.getNodes() != null) {
            for (DirectoryTreeNode subNode : node.getNodes()) {
                buildInitialCache(subNode.getFullPath());
            }
        }
    }

    private void prepareFilesForCache(File item, List<ServerMediaFile> currentFiles) {
        File[] tmpFiles;
        if (item.isDirectory()) {
            tmpFiles = item.listFiles(File::isFile);
        } else {
            tmpFiles = new File[]{item};
        }
        if (tmpFiles == null || tmpFiles.length == 0) {
            return;
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

        // build output
        List<ServerMediaFile> mediaFiles = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            // file should have real path, parent should have absolute path
            mediaFiles.add(new ServerMediaFile(files.get(i), parents.get(i),
                    ioUtils.getMimeType(files.get(i).getAbsolutePath())));
        }

        currentFiles.addAll(mediaFiles);
    }

    private void addToCache(File item, WatchService watcher, Map<WatchKey, Path> watcherMap) {
        if (item == null) return;

        Path parentFilePath = Paths.get(item.getParentFile().getAbsolutePath());
        Path filePath = Paths.get(item.getAbsolutePath());

        // update cache if it exists; else log an error
        if (fileCache.get(parentFilePath) != null) {
            if (item.isDirectory()) {
                // if directory is a symlink, it may already have files in it
                ArrayList<ServerMediaFile> fileList = new ArrayList<>();
                prepareFilesForCache(item, fileList);
                fileCache.put(filePath, new ServerMediaListing(fileList));
                walkAndRegisterDirectories(Paths.get(item.getAbsolutePath()), watcher, watcherMap);
                log.debug("Directory added to cache: " + item.getAbsolutePath());
            } else {
                ServerMediaListing oldListing = fileCache.get(parentFilePath);
                List<ServerMediaFile> mediaFiles = new ArrayList<>(oldListing.getData());
                prepareFilesForCache(item, mediaFiles);

                // update cache
                ServerMediaListing newListing = new ServerMediaListing(mediaFiles);
                fileCache.put(parentFilePath, newListing);
                log.debug("File" + item.getName() + " added to cache: " + filePath);
            }
            // Update directory tree
            DirectoryTreeNode oldNode =  new DirectoryTreeNode(propertiesUtil.getRemoteMediaDirectory());
            rootDirectoryTreeCache = oldNode.fillDirectoryTree(oldNode,
                    new ArrayList<>(), propertiesUtil.getServerMediaTreeRoot());
        } else {
            log.error("Item created in directory that was not cached: " + item.getAbsolutePath());
        }
    }

    private void removeFromCache(File item, Map<WatchKey, Path> watcherMap) {
        if (item == null) return;

        Path removedItem = item.toPath().toAbsolutePath();
        Path parentDir = removedItem.getParent();

        // update cache if it exists; else do nothing
        if (fileCache.get(parentDir) != null) {
            // cannot determine file type since it has been deleted
            if (fileCache.get(removedItem) != null) {
                // directory
                // The OS will delete files from the lowest level up, therefore we do not need to worry about
                // subdirectories or sub files. This directory is guaranteed to be empty since those deletion events
                // will have cleared everything below it from the cache
                fileCache.remove(removedItem);
                WatchKey deletedDirKey = null;
                for (WatchKey key : watcherMap.keySet()) {
                    Path path = watcherMap.get(key);
                    if (path.equals(Paths.get(item.getAbsolutePath()))) {
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
                ServerMediaListing oldListing = fileCache.get(parentDir);

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
                fileCache.put(parentDir, newListing);
            }
            // update directory tree
            DirectoryTreeNode oldNode = new DirectoryTreeNode(propertiesUtil.getRemoteMediaDirectory());
            rootDirectoryTreeCache = oldNode.fillDirectoryTree(oldNode,
                    new ArrayList<>(), propertiesUtil.getServerMediaTreeRoot());
            log.debug("Item " + item.getName() + " remove from cache: " + item.getAbsolutePath());
        } else {
            log.error("Item deleted in a directory that was not cached: " + item.getAbsolutePath());
        }
    }

}
