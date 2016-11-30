/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.lang3.tuple.Pair;
import org.mitre.mpf.mvc.model.DirectoryTreeNode;
import org.mitre.mpf.mvc.model.ServerMediaFile;
import org.mitre.mpf.mvc.model.ServerMediaFilteredListing;
import org.mitre.mpf.mvc.model.ServerMediaListing;
import org.mitre.mpf.wfm.util.IoUtils;
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

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Api(value = "Server Media",description = "Server media retrieval")
@Controller
@Scope("request")
@Profile("website")
public class ServerMediaController {
	
	private static final Logger log = LoggerFactory.getLogger(ServerMediaController.class);

	public static final String DEFAULT_ERROR_VIEW = "error";
	public static final String SESSION_DIRECTORY_STRUCTURE = "DirectoryStructure";

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private IoUtils ioUtils;

	private List<String> customExtensions = null;
	
	@PostConstruct
	public void postConstruct()  {
		this.customExtensions = propertiesUtil.getServerMediaTreeCustomExtensions();
	}

	public static DirectoryTreeNode getAllDirectories(String nodePath,HttpSession session,boolean useSession,String uploadDir){
		if(session.getAttribute(SESSION_DIRECTORY_STRUCTURE) != null && useSession){
			log.debug("Using session directory structure");
			return (DirectoryTreeNode)session.getAttribute(SESSION_DIRECTORY_STRUCTURE);
		}

		DirectoryTreeNode node = null;
		try {
			node = DirectoryTreeNode.fillDirectoryTree(new DirectoryTreeNode(new File(nodePath)),uploadDir);
			session.setAttribute(SESSION_DIRECTORY_STRUCTURE,node);
		} catch (IOException e) {
			log.error("getAllDirectories error: "+e.getMessage());
		}

		return node;
	}

	@RequestMapping(value = { "/server/get-all-directories" }, method = RequestMethod.GET)
	@ResponseBody
	public DirectoryTreeNode getAllDirectories(HttpServletRequest request,@RequestParam(required = false) Boolean useUploadRoot, @RequestParam(required = false,defaultValue = "true") boolean useCache){
		HttpSession session = request.getSession();
		String nodePath = propertiesUtil.getServerMediaTreeRoot();

		//if useUploadRoot is set it will take precedence over nodeFullPath
		DirectoryTreeNode node = getAllDirectories(nodePath,session,useCache,propertiesUtil.getRemoteMediaCacheDirectory().getAbsolutePath());
		if(useUploadRoot != null && useUploadRoot){
			node =  DirectoryTreeNode.find(node,propertiesUtil.getRemoteMediaCacheDirectory().getAbsolutePath());
		}
		return node;
	}

	@RequestMapping(value = { "/server/get-all-files" }, method = RequestMethod.GET)
	@ResponseBody
	public ServerMediaListing getAllFiles(@RequestParam(required = true) String fullPath,@RequestParam(required = false) boolean recurse) {
		File dir = new File(fullPath);
		if(!dir.isDirectory() && fullPath.startsWith(propertiesUtil.getServerMediaTreeRoot())) {
            return null;
        }

        List<ServerMediaFile> mediaFiles = new ArrayList<>();
		if(recurse){
			mediaFiles = getRemoteMediaFilesRecursive(dir);
		} else{
			for(File file:dir.listFiles()){
				if(file.isFile()) {
					String mimeType = ioUtils.getMimeType(file.getAbsolutePath());
					mediaFiles.add(new ServerMediaFile(file, mimeType));
				}
			}
		}
        return new ServerMediaListing(mediaFiles);
	}

	private List<ServerMediaFile> getRemoteMediaFilesRecursive(File root) {
        try {
            return Files.walk(root.toPath())
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .map(f -> Pair.of(f, ioUtils.getMimeType(f.getAbsolutePath())))
                    .filter(p -> p.getRight() != null)
                    .map(p -> new ServerMediaFile(p.getLeft(), p.getRight()))
                    .collect(toList());
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

	//https://datatables.net/manual/server-side#Sent-parameters
	//draw is the counter of how many times it has called back
	//length is how many to return
	//start is offset from 0
	//search is string to filter
	@RequestMapping(value = { "/server/get-all-files-filtered" }, method = RequestMethod.POST)
	@ResponseBody
	public ServerMediaFilteredListing getAllFilesFiltered(@RequestParam(value="fullPath", required=true) String fullPath,
									  @RequestParam(value="draw", required=false) int draw,
									  @RequestParam(value="start", required=false) int start,
									  @RequestParam(value="length", required=false) int length,
									  @RequestParam(value="search", required=false) String search,
									  @RequestParam(value="sort", required=false) String sort){
		log.debug("Params fullPath:{} draw:{} start:{},length:{},search:{} ",fullPath,draw,start,length,search,sort);

		File dir = new File(fullPath);
		if(!dir.isDirectory() && fullPath.startsWith(propertiesUtil.getServerMediaTreeRoot())) return null;//security check

		File[] files = dir.listFiles(File::isFile);

		//sort it by filename modified date (most current first)
		if(sort != null && sort == "lastModified") {
			Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
		}else{
            Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR
					// Then make capital letters come before lowercase
					.thenComparing(NameFileComparator.NAME_COMPARATOR));
		}

		//handle search
		if(search != null && search.length() > 0) {
			List<File> search_results = new ArrayList<File>();
			for (int i = 0; i < files.length; i++) {
				File file = files[i];
				if (files[i].getName().toLowerCase().contains(search.toLowerCase())) {
					search_results.add(file);
				}
			}
			files = new File[search_results.size()];
			files=search_results.toArray(files);
		}

		//filter by approved list of content type
		List<File> contentFiltered = new ArrayList<File>();
		for(int i =0;i<files.length;i++) {
			File file = files[i];
			if (ioUtils.isApprovedFile(file) ) {
				contentFiltered.add(file);
			}
		}
		files = new File[contentFiltered.size()];
		files=contentFiltered.toArray(files);

		int records_total = files.length;
		int records_filtered = records_total;// Total records, after filtering (i.e. the total number of records after filtering has been applied - not just the number of records being returned for this page of data).


		//handle paging
		int end = start +length;
		end = (end > files.length)? files.length : end;
		start = (start<=end)? start : end;
		File[] filtered = Arrays.copyOfRange(files,start,end);

		List<ServerMediaFile> mediaFiles = new ArrayList<>();
		//build output
		for(int i =0;i<filtered.length;i++) {
			File file = filtered[i];
			if (ioUtils.isApprovedFile(file) ) {
                mediaFiles.add(new ServerMediaFile(file, ioUtils.getMimeType(file.getAbsolutePath())));
			}
		}

		return new ServerMediaFilteredListing(draw, records_filtered, records_total, mediaFiles);
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
}