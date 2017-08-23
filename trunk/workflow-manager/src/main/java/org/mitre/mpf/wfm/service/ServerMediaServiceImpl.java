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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ServerMediaServiceImpl implements ServerMediaService {

	private static final Logger log = LoggerFactory.getLogger(ServerMediaServiceImpl.class);

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private IoUtils ioUtils;

	public DirectoryTreeNode getAllDirectories(String nodePath, ServletContext context, boolean useCache, String uploadDir){
		// attempt to get attribute from application scope
		String attributeName = CACHED_DIRECTORY_STRUCTURE_PREFIX + nodePath;

		if(context.getAttribute(attributeName) != null && useCache){
			log.debug("Using cached directory structure: " + nodePath);
			return (DirectoryTreeNode)context.getAttribute(attributeName);
		}

		// update cache
		DirectoryTreeNode node = DirectoryTreeNode.fillDirectoryTree(new DirectoryTreeNode(new File(nodePath)), new ArrayList<DirectoryTreeNode>(), uploadDir);
		context.setAttribute(attributeName, node);

		return node;
	}

	public List<ServerMediaFile> getFiles(DirectoryTreeNode node, ServletContext context, boolean useCache, boolean recurse) {
		List<ServerMediaFile> mediaFiles = new ArrayList<>();
		File dir = new File(node.getFullPath());

		// attempt to get attribute from application scope
		String attributeName = CACHED_FILES_PREFIX + node.getFullPath();

		if (context.getAttribute(attributeName) != null && useCache){
			log.debug("Using cached file listing: " + node.getFullPath());
			ServerMediaListing listing = (ServerMediaListing)context.getAttribute(attributeName);
			mediaFiles.addAll(listing.getData());
		} else {
			File[] tmpFiles = dir.listFiles(File::isFile);

			List<File> files = new ArrayList<>(); // use real paths
			List<File> parents = new ArrayList<>(); // use absolute paths
			for (int i = 0; i < tmpFiles.length; i++) {
				try {
					files.add(tmpFiles[i].toPath().toRealPath().toFile()); // resolve symbolic links
					parents.add(tmpFiles[i].getParentFile());
				} catch (IOException e) {
					log.error("Error determining real path: " + e.getMessage());
				}
			}

			// filter by approved list of content type
			List<File> filesFiltered = new ArrayList<>();
			List<File> parentsFiltered = new ArrayList<>();
			for (int i = 0; i < files.size(); i++) {
				if (ioUtils.isApprovedFile(files.get(i))) {
					filesFiltered.add(files.get(i));
					parentsFiltered.add(parents.get(i));
				}
			}
			files = filesFiltered;
			parents = parentsFiltered;

			// build output
			for (int i = 0; i < files.size(); i++) {
				// file should have real path, parent should have absolute path
				mediaFiles.add(new ServerMediaFile(files.get(i), parents.get(i), ioUtils.getMimeType(files.get(i).getAbsolutePath())));
			}

			// update cache
			ServerMediaListing listing = new ServerMediaListing(mediaFiles);
			context.setAttribute(attributeName, listing);
		}

		// recurse
		if (recurse && node.getNodes() != null) {
			for (DirectoryTreeNode subnode : node.getNodes()) {
				mediaFiles.addAll(getFiles(subnode, context, useCache, recurse));
			}
		}

		return mediaFiles;
	}

	public List<ServerMediaFile> getFiles(String dirPath, ServletContext context, boolean useCache, boolean recurse) {
		DirectoryTreeNode node = getAllDirectories(propertiesUtil.getServerMediaTreeRoot(), context,
				useCache, propertiesUtil.getRemoteMediaCacheDirectory().getAbsolutePath());
		node = DirectoryTreeNode.find(node, dirPath);
		return getFiles(node, context, useCache, recurse);
	}

	public void addFilesToCache(String dirPath, List<File> files, ServletContext context) {
		if (files.isEmpty()) return;

		// attempt to get attribute from application scope
		String attributeName = CACHED_FILES_PREFIX + dirPath;

		// update cache if it exists; else do nothing
		if (context.getAttribute(attributeName) != null){
			ServerMediaListing oldListing = (ServerMediaListing)context.getAttribute(attributeName);
			List<ServerMediaFile> mediaFiles = new ArrayList<>(oldListing.getData());

			for (File file : files) {
				try {
					File realFile = file.toPath().toRealPath().toFile(); // use real path
					File parent = file.getParentFile(); // use absolute path
					mediaFiles.add(new ServerMediaFile(realFile, parent, ioUtils.getMimeType(realFile.getAbsolutePath())));
				} catch (IOException e) {
					log.error("Error determining real path: " + e.getMessage());
				}
			}

			// update cache
			ServerMediaListing newListing = new ServerMediaListing(mediaFiles);
			context.setAttribute(attributeName, newListing);
		}
	}

	public void addFileToCache(String dirPath, File file, ServletContext context) {
		addFilesToCache(dirPath, Arrays.asList(file), context);
	}
}