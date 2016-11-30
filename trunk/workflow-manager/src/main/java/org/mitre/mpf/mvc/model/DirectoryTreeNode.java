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

package org.mitre.mpf.mvc.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DirectoryTreeNode {
    private static final Logger log = LoggerFactory.getLogger(DirectoryTreeNode.class);
    private String text = null; //file or dir text/path;
    private String fullPath = null;
    private List<DirectoryTreeNode> nodes = null;
    private boolean canUpload = false;

    public DirectoryTreeNode(File f) {
        this.text = f.getName();
        this.fullPath = f.getAbsolutePath();
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

    public List<DirectoryTreeNode> getNodes() {
        return this.nodes;
    }

    public void addNode(DirectoryTreeNode node) {
        this.nodes.add(node);
    }

    public boolean isCanUpload() {
        return canUpload;
    }

    public void setCanUpload(boolean canUpload) {
        this.canUpload = canUpload;
    }

    public static DirectoryStream.Filter<Path> DirectoryFilter = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return (Files.isDirectory(entry));// && !Files.isSymbolicLink(entry));
        }
    };

    public static DirectoryTreeNode fillDirectoryTree(DirectoryTreeNode node,String uploadDir ) throws IOException {
        Path folder = Paths.get(node.getFullPath());
        List<Path> dirs = new ArrayList<Path>();

        DirectoryStream<Path> stream = Files.newDirectoryStream(folder, DirectoryFilter);
        for (Path entry : stream) {
            dirs.add(entry);
        }
        stream.close();

        if(dirs.size() > 0) {
            node.nodes = new ArrayList<DirectoryTreeNode>();
            for (Path child : dirs) {
                if(Files.isSymbolicLink(child)) log.info("adding symbolic link to directory structure:"+child.toAbsolutePath());//just FYI incase things get hairy on server
                node.addNode(fillDirectoryTree(new DirectoryTreeNode(child.toFile()),uploadDir)); //recursion takes too long on large filesystems with thousands of files
            }
            Comparator<String> stableCaseInsensitive = String.CASE_INSENSITIVE_ORDER
                    .thenComparing(Comparator.naturalOrder());
            node.nodes.sort(Comparator.comparing(DirectoryTreeNode::getText, stableCaseInsensitive));
        }
        if (node.getFullPath().startsWith(uploadDir)) {
            node.canUpload = true;
        }
        return node;
    }

    public static DirectoryTreeNode find(DirectoryTreeNode node,String fullPath) {
        if (node.getFullPath().equals(fullPath)) return node;
        if (node.nodes != null) {
            for (DirectoryTreeNode child : node.nodes) {
                if (find(child, fullPath) != null)
                    return child;
            }
        }
        return null;
    }


}	
