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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

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

    // Map keys to a list of paths. The list may contain multiple paths that symlink to the same real path.
    private Map<WatchKey, List<Path>> watcherMap = new HashMap<>();

    // TODO: Create map of symlink realpath to absolute paths; revert watcher map to real path;
    // on real path event check use item to get absolute paths from symlink map; use paths to update file cache

    // TODO: use same file server listing for symlinks in file cache



    private boolean watcherInstantiated = false;

    @Inject
    public FileWatcherServiceImpl(PropertiesUtil propertiesUtil, IoUtils ioUtils) {
        this.propertiesUtil = propertiesUtil;
        this.ioUtils = ioUtils;
        updateRootDirectoryTreeCache();
    }

    private synchronized void updateRootDirectoryTreeCache() {
        File mediaRoot = propertiesUtil.getRemoteMediaDirectory();
        rootDirectoryTreeCache = new DirectoryTreeNode(mediaRoot);

        List<DirectoryTreeNode> seenNodes = new ArrayList<>();
        seenNodes.add(rootDirectoryTreeCache);

        rootDirectoryTreeCache.fillDirectoryTree(rootDirectoryTreeCache, seenNodes);
    }

    public synchronized DirectoryTreeNode getRootDirectoryTreeCache() {
        return rootDirectoryTreeCache;
    }

    public ServerMediaListing getFileListByPath(Path path) {
        return fileCache.get(path);
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

    private void walkAndRegisterDirectories(final Path start, WatchService watcher) {
        // register directory and sub-directories
        try {
            SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!registerDirectory(dir, watcher)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    addFilesToCacheNoRecurse(dir.toFile(), new ArrayList<>());
                    return FileVisitResult.CONTINUE;
                }
                public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    if (ex instanceof FileSystemLoopException) {
                        log.warn("Detected cycle while indexing file system. May be due to a symlink. Ignoring file: {}", file);
                    } else {
                        log.error("Encountered error while indexing file system. Ignoring file: {}: {}", file, ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(start, Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
        } catch (IOException e) {
            log.error("Failed to register file directory tree under: " + start);
            throw new UncheckedIOException(e);
        }
    }

    private boolean registerDirectory(Path dir, WatchService watcher) {
        // register the current media directory with a file watcher service, listening for file creation or deletion
        if (!isValidDirectory(dir)) {
            return false;
        }

        WatchKey key;
        try {
            key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE); // TODO: realpath()
            log.debug("File watcher registered for directory: " + dir);
            if (watcherMap.containsKey(key)) {
                watcherMap.get(key).add(dir);
            } else {
                List<Path> dirs = new ArrayList<>();
                dirs.add(dir);
                watcherMap.put(key, dirs);
                log.info("watcherMap.put: " + dirs); // DEBUG
            }
            /*
            if (Files.isSymbolicLink(dir)) {
                // need to register parent dir too since it may be outside web tree root
                registerDirectory(dir.toRealPath().getParent(), watcher);
            }
            */
        } catch (IOException e) {
            log.error("Failed to register file watcher: " + dir);
            throw new UncheckedIOException(e);
        }

        return true;
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

        // Initialize caches
        walkAndRegisterDirectories(cacheFolder, watcher);

        // Load existing files on startup since we won't see their creation event
        //buildInitialCache(propertiesUtil.getServerMediaTreeRoot());

        log.info("File watcher task started");
        fileEventLoop(watcher);
    }

    private void fileEventLoop(WatchService watcher) {
        try {
            WatchKey key;
            while ((key = watcher.take()) != null) { // blocks until watcher.take() returns an event
                List<Path> dirs = watcherMap.get(key);
                if (dirs == null) {
                    // watch key not recognized or removed
                    continue;
                }

                dirs = new ArrayList<>(watcherMap.get(key)); // copy to prevent concurrent access exception

                for (WatchEvent<?> event : key.pollEvents()) {
                    //process
                    log.debug("File event occurred in watched directory");
                    Path eventPath = ((WatchEvent<Path>) event).context();
                    for (Path dir : dirs) {
                        Path child = dir.resolve(eventPath);
                        if (event.kind() == ENTRY_CREATE) {
                            File newItem = new File(child.toAbsolutePath().toString());
                            addEntryToCache(newItem, watcher);
                        } else if (event.kind() == ENTRY_DELETE) {
                            File removedItem = new File(child.toAbsolutePath().toString());
                            removeEntryFromCache(key, removedItem);
                        } else {
                            log.debug("Unknown event type occurred: " + event.kind() + ", context: " + event.context());
                        }
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

    private void addFilesToCacheNoRecurse(File item, List<ServerMediaFile> currentFiles) {
        File[] tmpFiles;
        if (item.isDirectory()) {
            tmpFiles = item.listFiles(File::isFile); // only includes files one level deep
        } else {
            tmpFiles = new File[]{item};
        }
        if (tmpFiles == null) { // possible permissions issue
            return;
        }

        List<ServerMediaFile> mediaFiles = new ArrayList<>();
        for (int i = 0; i < tmpFiles.length; i++) {
            // file should have real path, parent should have absolute path
            File file = tmpFiles[i].toPath().toAbsolutePath().toFile();
            mediaFiles.add(new ServerMediaFile(file, tmpFiles[i].getParentFile(),
                    ioUtils.getMimeType(file.getAbsolutePath())));
        }

        currentFiles.addAll(mediaFiles);

        log.info("addFilesToCacheNoRecurse: " + mediaFiles.stream().map(ServerMediaFile::getFullPath).collect(Collectors.joining(", "))); // DEBUG

        if (item.isDirectory()) {
            fileCache.put(item.toPath().toAbsolutePath(), new ServerMediaListing(currentFiles));
        } else {
            fileCache.put(item.getParentFile().toPath().toAbsolutePath(), new ServerMediaListing(currentFiles));
        }
    }

    private boolean isValidDirectory(File file) {
        return isValidDirectory(file.toPath());
    }

    private boolean isValidDirectory(Path path) {
        Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (IOException e) {
            log.error("Error determining real path: " + e.getMessage());
            return false;
        }

        if (realPath == null) {
            return false;
        }

        if (fileCache.containsKey(realPath)) {
            log.warn("Omitting previously seen directory: " + path + " --> " + realPath);
            return false;
        }

        return true;
    }

    private void addEntryToCache(File item, WatchService watcher) {
        if (item == null) {
            return;
        }

        log.info("addEntryToCache: " + item); // DEBUG

        Path parentFilePath = Paths.get(item.getParentFile().getAbsolutePath());
        Path filePath = Paths.get(item.getAbsolutePath());

        // update cache if it exists; else log a warning
        if (fileCache.get(parentFilePath) == null) {
            log.warn("Item created in directory that was not cached: " + item.getAbsolutePath());
            return;
        }

        if (item.isDirectory()) {
            if (isValidDirectory(item)) {
                walkAndRegisterDirectories(Paths.get(item.getAbsolutePath()), watcher);

                addFilesToCacheNoRecurse(item, new ArrayList<>());

                log.info("Directory added to cache: " + item.getAbsolutePath()); // DEBUG
                updateRootDirectoryTreeCache();
            }
        } else {
            ServerMediaListing oldListing = fileCache.get(parentFilePath);
            addFilesToCacheNoRecurse(item, new ArrayList<>(oldListing.getData())); // DEBUG

            log.info("File added to cache: " + filePath);
            updateRootDirectoryTreeCache();
        }
    }

    private void removeEntryFromCache(WatchKey key, File item) {
        if (item == null) {
            return;
        }

        log.info("removeEntryFromCache: " + item); // DEBUG

        Path removedItem = item.toPath().toAbsolutePath();
        Path parentDir = removedItem.getParent();

        // if this item is a dir in the web root tree, it will be in the file cache
        if (fileCache.remove(removedItem) != null) {
            log.info("HERE A"); // DEBUG

            // remove sub-directories from the file cache too
            fileCache.keySet().removeIf(k -> k.startsWith(item.getAbsolutePath()));
            updateRootDirectoryTreeCache();

        // else this item is a parent dir of a symlink, or a file;
        // if a file, its parent dir will be in the file cache
        } else if (fileCache.containsKey(parentDir)) {
            log.info("HERE B"); // DEBUG

            if (fileCache.get(parentDir).getData().removeIf(smf -> smf.getFullPath().equals(item.getAbsolutePath()))) {
                updateRootDirectoryTreeCache();
            }
        }

        // if this item is a dir, it will appear as a value in the watcher map
        List<Path> dirs = watcherMap.get(key);
        List<Path> dirsCopy = new ArrayList<>(dirs); // DEBUG
        if (dirs.removeIf(d -> d.equals(item.getAbsolutePath()))) { // TODO: All paths need to be removed?
            if (dirs.isEmpty()) {
                log.info("1 watcherMap.remove: " + dirs); // DEBUG
                key.reset();
                key.cancel();
                watcherMap.remove(key);
            }
        }

        // TODO: Improve lookup time by making watch keys part of root directory tree cache.
        Set<WatchKey> keysToRemove = new HashSet<>();
        for (WatchKey k : watcherMap.keySet()) {
            List<Path> subdirs = watcherMap.get(k);
            List<Path> subdirsCopy = new ArrayList<>(subdirs); // DEBUG
            if (subdirs.removeIf(d -> d.startsWith(item.getAbsolutePath()))) { // TODO: All paths need to be removed?
                if (subdirs.isEmpty()) {
                    log.info("2 watcherMap.remove: " + subdirsCopy); // DEBUG
                    k.reset();
                    k.cancel();
                    keysToRemove.add(k);
                }
            }
        }
        for (WatchKey keyToRemove : keysToRemove) {
            watcherMap.remove(keyToRemove);
        }

        // DEBUG
        for (WatchKey k : watcherMap.keySet()) {
            log.info("watcherMap left: " + watcherMap.get(k)); // DEBUG
        }
    }
}
