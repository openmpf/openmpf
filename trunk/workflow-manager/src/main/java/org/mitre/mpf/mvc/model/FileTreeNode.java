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

package org.mitre.mpf.mvc.model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class FileTreeNode {		
	private String text = null; //file or dir name/path;
	//TODO: could create a map of id's and fullPaths to abstract the full path coming from the server
	private  String fullPath = null;
	private int tags = 0;
	//private boolean isFile = false;
	//has to be named file to sync with the client side
	private boolean file = false;
	private  List<FileTreeNode> nodes = new ArrayList<FileTreeNode>();
	
	FileTreeNodeState state = new FileTreeNodeState();
	
	//closed folder
	// glyphicon glyphicon-folder-close	
	//open folder
	// glyphicon glyphicon-folder-open
	private String icon = "glyphicon glyphicon-folder-close";
						
	public FileTreeNode() {}
	
	public FileTreeNode(String text) {
		this.text = text;
		//assume text is the fullPath using this constructor
		this.fullPath = text;
		//this is the parent folder - the only use for this constructor at the moment
		this.icon = "glyphicon glyphicon-folder-open";
		
		//set the parent folder to expanded, but set the other new ones to false or they will expand by default
		this.state.expanded = true;
	}
	
	public FileTreeNode(File f) {
		this.text = f.getName();
		this.fullPath = f.getAbsolutePath();
		if(f.isFile()) {
			this.file = true;	
			//this will prevent the node from having the ability to expand
			this.nodes = null;
			
			//TODO: move this checking to a method!
			String name = f.getName();

			String extension = "";
			int i = name.lastIndexOf('.');
			if (i > 0) {
				extension = name.toLowerCase().substring(i + 1);
			}

			//TODO: would save processing if not going from Java 6 to Java 7/8
			Path p = Paths.get(this.fullPath, name);
			String contentType = "";
			try {
				contentType = Files.probeContentType(p);
			}
		   	catch (Exception ex){
		   		//TODO: a logger in here would probably break serialization - should move the content retrieval to another method
		      	//log.error("An exception occurred when trying to determine the content type of the file '{}': {}", f.getAbsolutePath(), ex);
		    }
							
			//photo ?
			// glyphicon glyphicon-picture
			//audio ?
			// glyphicon glyphicon-music	
			//video ?
			// glyphicon glyphicon-film
			
			if(StringUtils.startsWithIgnoreCase(contentType, "AUDIO")) {
				this.icon = "glyphicon glyphicon-music";
			} 
			else if(StringUtils.startsWithIgnoreCase(contentType, "IMAGE")) {
				this.icon = "glyphicon glyphicon-picture";
			}
			else if(StringUtils.startsWithIgnoreCase(contentType, "VIDEO")) {
				this.icon = "glyphicon glyphicon-film";
			}
			else {
				//TODO: doesn't display an mkv, capital JPG				
				//TODO: should not be included!!
				//TOOD: could use the extension as a backup if the content-type is ignored
				this.icon = "glyphicon glyphicon-file";
			}
		} else {
			//directory
			if(f.list().length <= 0) {
				//icon should be a closed folder by default
				this.nodes = null;
			}
			
			//set the expansion to false because the node will automatically expand if/when nodes are added
			//the expanded state will stay with the node
			this.state.expanded = false;
		} 
	}
	
	public String getText() {
		return this.text;
	}
	public void setText(String text) {
		this.text = text;
	}
	
	public String getFullPath() {
		return this.fullPath;
	}
	public void setFullPath(String path) {
		this.fullPath = path;
	}
	
	public int getTags() {
		return this.tags;
	}
	
	public List<FileTreeNode> getNodes() {
		return this.nodes;
	}

	public String getIcon() {
		return this.icon;
	}

	public boolean isFile() {
		return this.file;
	}
	
	public FileTreeNodeState getState() {
		return this.state;
	}

	//when nodes have been set to null, but now are being updated because 
	//child nodes now exist
	public void initializeNodes() {
		this.nodes = new ArrayList<FileTreeNode>();		
	}

	//this method can be used when all of the sub files and directories
	//have been removed from a directory on the file system and the client side
	//needs to be updated
	//TODO: solution for removing specific files and directories only
	public void removeNodes() {
		this.nodes = null;
	}
	
	
	@Override
	public boolean equals(Object object) {
	    boolean isEqual= false;
	    if (object != null && object instanceof FileTreeNode) {
	    	//String compare
	        isEqual = (this.fullPath.equals( ((FileTreeNode) object).fullPath) );
	    }
	    return isEqual;
	}
}	
