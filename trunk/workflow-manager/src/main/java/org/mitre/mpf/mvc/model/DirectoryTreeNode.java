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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
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

    public static DirectoryStream.Filter<Path> DirectoryFilter = entry -> (Files.isDirectory(entry));

    public static DirectoryTreeNode fillDirectoryTree(DirectoryTreeNode node, List<DirectoryTreeNode> seenNodes, String uploadDir) {

        List<Path> dirs = new ArrayList<Path>();
        Path folder = Paths.get(node.getFullPath());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, DirectoryFilter)) {
            for (Path entry : stream) {
                dirs.add(entry);
            }
        } catch (IOException e) {
            log.error("Error detecting directories: " + e.getMessage());
            return node;
        }

        if(dirs.size() > 0) {
            node.nodes = new ArrayList<DirectoryTreeNode>();
            for (Path child : dirs) {
                Path realPath = null;
                try {
                    realPath = child.toRealPath();
                } catch (IOException e) {
                    log.error("Error determining real path: " + e.getMessage());
                }
                if (realPath != null) {
                    DirectoryTreeNode realChildNode = new DirectoryTreeNode(realPath.toFile()); // resolve symbolic link to real path

                    if (Files.isSymbolicLink(child)) {
                        if (seenNodes.contains(realChildNode)) {
                            log.warn("Omitting duplicate symbolically linked directory in this branch: " + child.toAbsolutePath() + " --> " + realPath);
                            continue; // prevent symlink loop
                        }
                        log.info("Adding symbolically linked directory: " + child.toAbsolutePath() + " --> " + realPath);
                    }

                    // only keep track of the nodes we've seen in this branch of the file tree;
                    // it's okay for two separate branches to have the same symbolic links as long as there are no cycles
                    List<DirectoryTreeNode> seenNodesCopy = new ArrayList<DirectoryTreeNode>();
                    seenNodesCopy.addAll(seenNodes);
                    seenNodesCopy.add(realChildNode);

                    node.addNode(fillDirectoryTree(new DirectoryTreeNode(child.toFile()), seenNodesCopy, uploadDir)); // use absolute path
                }
            }
            if (node.nodes.isEmpty()) {
                node.nodes = null; // don't show a + for this node in the directory tree if it has no children
            } else {
                Comparator<String> stableCaseInsensitive = String.CASE_INSENSITIVE_ORDER
                        .thenComparing(Comparator.naturalOrder());
                node.nodes.sort(Comparator.comparing(DirectoryTreeNode::getText, stableCaseInsensitive));
            }
        }

        if (node.getFullPath().startsWith(uploadDir)) {
            node.canUpload = true;
        }

        return node;
    }

    public static DirectoryTreeNode find(DirectoryTreeNode node,String fullPath) {
        if (node.getFullPath().equals(fullPath)) return node;
        if (node.nodes != null) {
            DirectoryTreeNode found = null;
            for (DirectoryTreeNode child : node.nodes) {
                if ((found = find(child, fullPath)) != null)
                    return found;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof  DirectoryTreeNode) {
            DirectoryTreeNode other = (DirectoryTreeNode)obj;
            return fullPath.equals(other.getFullPath());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
}	
