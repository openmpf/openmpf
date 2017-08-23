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

import javax.servlet.ServletContext;
import java.io.File;
import java.util.List;

public interface ServerMediaService {

	// NOTE: Since all users have access to the same directory structure, it is optimal to cache directory and
	// file information at the application level (as opposed to the session level).
	public static final String CACHED_DIRECTORY_STRUCTURE_PREFIX = "DirectoryStructure:";
	public static final String CACHED_FILES_PREFIX = "Files:";

	public DirectoryTreeNode getAllDirectories(String nodePath, ServletContext context, boolean useCache, String uploadDir);

	public List<ServerMediaFile> getFiles(DirectoryTreeNode node, ServletContext context, boolean useCache, boolean recurse);

	public List<ServerMediaFile> getFiles(String dirPath, ServletContext context, boolean useCache, boolean recurse);

	public void addFilesToCache(String dirPath, List<File> files, ServletContext context);

	public void addFileToCache(String dirPath, File file, ServletContext context);
}