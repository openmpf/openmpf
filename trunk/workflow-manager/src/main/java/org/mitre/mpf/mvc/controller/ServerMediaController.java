/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import io.swagger.annotations.Api;
import org.apache.commons.io.FileUtils;
import org.mitre.mpf.mvc.model.DirectoryTreeNode;
import org.mitre.mpf.mvc.model.ServerMediaFile;
import org.mitre.mpf.mvc.model.ServerMediaFilteredListing;
import org.mitre.mpf.mvc.model.ServerMediaListing;
import org.mitre.mpf.wfm.service.ServerMediaService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.*;

@Api(value = "Server Media",description = "Server media retrieval")
@Controller
@Scope("request")
@Profile("website")
public class ServerMediaController {
	
	private static final Logger log = LoggerFactory.getLogger(ServerMediaController.class);

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private ServerMediaService serverMediaService;

	public class SortAlphabeticalCaseInsensitive implements Comparator<Object> {
		public int compare(Object o1, Object o2) {
			ServerMediaFile s1 = (ServerMediaFile)o1;
			ServerMediaFile s2 = (ServerMediaFile)o2;
			return s1.getName().toLowerCase().compareTo(s2.getName().toLowerCase());
		}
	}

	public class SortAlphabeticalCaseSensitive implements Comparator<Object> {
		public int compare(Object o1, Object o2) {
			ServerMediaFile s1 = (ServerMediaFile)o1;
			ServerMediaFile s2 = (ServerMediaFile)o2;
			return s1.getName().compareTo(s2.getName());
		}
	}

	@RequestMapping(value = { "/server/get-all-directories" }, method = RequestMethod.GET)
	@ResponseBody
	public DirectoryTreeNode getAllDirectories(HttpServletRequest request, @RequestParam(required = false) Boolean useUploadRoot,
											   @RequestParam(required = false, defaultValue = "true") boolean useCache){
		String nodePath = propertiesUtil.getServerMediaTreeRoot();

		// if useUploadRoot is set it will take precedence over nodeFullPath
		DirectoryTreeNode node = serverMediaService.getAllDirectories(nodePath, request.getServletContext(), useCache,
				propertiesUtil.getRemoteMediaCacheDirectory().getAbsolutePath());
		if(useUploadRoot != null && useUploadRoot){
			node =  DirectoryTreeNode.find(node, propertiesUtil.getRemoteMediaCacheDirectory().getAbsolutePath());
		}

		return node;
	}

	@RequestMapping(value = { "/server/get-all-files" }, method = RequestMethod.GET)
	@ResponseBody
	public ServerMediaListing getAllFiles(HttpServletRequest request, @RequestParam(required = true) String fullPath,
										  @RequestParam(required = false, defaultValue = "true") boolean useCache) {
		File dir = new File(fullPath);
		if(!dir.isDirectory() && fullPath.startsWith(propertiesUtil.getServerMediaTreeRoot())) return null; // security check

		List<ServerMediaFile> mediaFiles = serverMediaService.getFiles(fullPath, request.getServletContext(), useCache, true);
		return new ServerMediaListing(mediaFiles);
	}

	//https://datatables.net/manual/server-side#Sent-parameters
	//draw is the counter of how many times it has called back
	//length is how many to return
	//start is offset from 0
	//search is string to filter
	@RequestMapping(value = { "/server/get-all-files-filtered" }, method = RequestMethod.POST)
	@ResponseBody
	public ServerMediaFilteredListing getAllFilesFiltered(HttpServletRequest request, @RequestParam(value="fullPath", required=true) String fullPath,
									  @RequestParam(required = false, defaultValue = "true") boolean useCache,
									  @RequestParam(value="draw", required=false) int draw,
									  @RequestParam(value="start", required=false) int start,
									  @RequestParam(value="length", required=false) int length,
									  @RequestParam(value="search", required=false) String search){
		log.debug("Params fullPath:{} draw:{} start:{} length:{} search:{} ",fullPath, draw, start, length, search);

		File dir = new File(fullPath);
		if(!dir.isDirectory() && fullPath.startsWith(propertiesUtil.getServerMediaTreeRoot())) return null; // security check

		List<ServerMediaFile> mediaFiles = serverMediaService.getFiles(fullPath, request.getServletContext(), useCache, false);

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

		return new ServerMediaFilteredListing(draw, mediaFiles.size(), mediaFiles.size(), mediaFiles.subList(start, end));
	}

	/***
	 *
	 * @param response
	 * @param nodeFullPath
	 * @throws IOException
	 * @throws URISyntaxException
     */
	@RequestMapping(value = "/server/node-image", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
	@ResponseBody
	public void serve(HttpServletResponse response, @RequestParam(value = "nodeFullPath", required = true) String nodeFullPath) throws IOException, URISyntaxException {
		//TODO: this set of lines is also used in the MarkupController - create a single method
		File f = new File(nodeFullPath);
		if(f.canRead()) {
			FileUtils.copyFile(f, response.getOutputStream());
			response.flushBuffer();
		} else {
			response.setStatus(404);
		}

		//TODO: add an image to return that is file not available and error retrieving file
		//to resources to use when there are issues

		//TODO: adjust the content type based on the image type
		//response.setContentLength(MediaType.);
	}

	/***
	 * Downloads a file from the server as attachment
	 * @param response
	 * @param fullPath
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	@RequestMapping(value = "/server/download", method = RequestMethod.GET)
	@ResponseBody
	public void download(HttpServletResponse response, @RequestParam(value = "fullPath", required = true) String fullPath) throws IOException, URISyntaxException {
		File file = new File(fullPath);
		if (file.exists() && file.canRead()) {
			String mimeType = URLConnection.guessContentTypeFromName(file.getName());
			if (mimeType == null) {
				System.out.println("mimetype is not detectable, will take default");
				mimeType = "application/octet-stream";
			}
			response.setContentType(mimeType);

			// "Content-Disposition : attachment" will be directly download, may provide save as popup, based on your browser setting
			response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", file.getName()));
			//"Content-Disposition : inline" will show viewable types [like images/text/pdf/anything viewable by browser] right on browser while others(zip e.g) will be directly downloaded [may provide save as popup, based on your browser setting.]
			//response.setHeader("Content-Disposition", String.format("inline; filename=\"" + file.getName() + "\""));

			response.setContentLength((int) file.length());

			FileUtils.copyFile(file, response.getOutputStream());
			response.flushBuffer();
		} else {
			log.debug("server download file failed "+fullPath);
			response.setStatus(404);
		}
	}
}