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

package org.mitre.mpf.mvc.util;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NIOUtils {
	
	private static final Logger log = LoggerFactory.getLogger(NIOUtils.class);
	
	public static long getSubFilesAndDirsCount(final Path p) {
		long count = 0;
		//help from http://stackoverflow.com/questions/18300105/number-of-subfolders-in-a-folder-directory
		try {
			count = Files.walk(p, 1, FileVisitOption.FOLLOW_LINKS).count() - 1; // '-1' because the Path param (p) is also counted in
		} catch (IOException e) {
			log.error("Error determing the count of sub files and directories.", e);
		}
		
		return count;
	}
	
	public static String getPathContentType(Path path) {
		String contentType = null;
		if(path != null && Files.isRegularFile(path)) {	    	
			try {
				contentType = Files.probeContentType(path);
			} catch (IOException e) {
				log.error("Error determining the content type of file '{}'", path.toAbsolutePath().toString());
			}
		}else{
			log.debug("Error determining the content type of file");
		}
		return contentType;
	}
	
	//if returned null, the file should not be added to the tree
    public static String getFileTreeNodeIconString(final Path path, final List<String> customExtensions) {
		//photo ?
		// glyphicon glyphicon-picture
		//audio ?
		// glyphicon glyphicon-music	
		//video ?
		// glyphicon glyphicon-film
		//other extensions
    	// glyphicon glyphicon-file
    	
    	String iconStr = null;
		if(path != null && Files.isRegularFile(path)) {
	    	String contentType = getPathContentType(path);			
	    	
			if(contentType != null) {
				if(StringUtils.startsWithIgnoreCase(contentType, "AUDIO")) {
					iconStr = "glyphicon glyphicon-music";
				} 
				else if(StringUtils.startsWithIgnoreCase(contentType, "IMAGE")) {
					iconStr = "glyphicon glyphicon-picture";
				}
				else if(StringUtils.startsWithIgnoreCase(contentType, "VIDEO")) {
					iconStr = "glyphicon glyphicon-film";
				}
				else if(customExtensions != null && !customExtensions.isEmpty()){
					//java NIO does not have an implemented solution for getting the extension - makes sense
					String ext = FilenameUtils.getExtension(path.getFileName().toString());
					if(customExtensions.contains(ext)) {
						iconStr = "glyphicon glyphicon-file";
					}
				}
			}
    	}
    	
    	return iconStr;
    }
}
