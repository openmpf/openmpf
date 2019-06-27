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
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServerMediaServiceImpl implements ServerMediaService {

	private static final Logger log = LoggerFactory.getLogger(ServerMediaServiceImpl.class);

	@Autowired
	private PropertiesUtil propertiesUtil;

	public DirectoryTreeNode getAllDirectories(String nodePath, ServletContext context, String uploadDir){
		// attempt to get attribute from application scope
		String attributeName = CACHED_DIRECTORY_STRUCTURE_PREFIX + nodePath;

		// TODO a directory might be deleted before the cache is updated on the front-end
		if(context.getAttribute(attributeName) != null){
			log.debug("Using cached directory structure: " + nodePath);
			return (DirectoryTreeNode)context.getAttribute(attributeName);
		} else {
			log.error("Media file cache is empty");
			File root = new File(nodePath);
			return new DirectoryTreeNode(root);
		}
	}

	public List<ServerMediaFile> getFiles(DirectoryTreeNode node, ServletContext context) {
		List<ServerMediaFile> mediaFiles = new ArrayList<>();

		// attempt to get attribute from application scope
		String attributeName = CACHED_FILES_PREFIX + node.getFullPath();

		if (context.getAttribute(attributeName) != null){
			log.debug("Using cached file listing: " + node.getFullPath());
			ServerMediaListing listing = (ServerMediaListing)context.getAttribute(attributeName);
			mediaFiles.addAll(listing.getData());
		} else {
			log.error("Media file cache not found");
		}

		return mediaFiles;
	}

	public List<ServerMediaFile> getFiles(String dirPath, ServletContext context) {
		DirectoryTreeNode node = getAllDirectories(propertiesUtil.getServerMediaTreeRoot(), context,
				propertiesUtil.getRemoteMediaDirectory().getAbsolutePath());
		node = DirectoryTreeNode.find(node, dirPath);
		return getFiles(node, context);
	}

}