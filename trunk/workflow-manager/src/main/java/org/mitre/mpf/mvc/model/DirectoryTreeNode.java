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
    private static DirectoryStream.Filter<Path> directoryFilter = entry -> (Files.isDirectory(entry));

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

    public List<DirectoryTreeNode> getNodes() {
        return this.nodes;
    }

    private void addNode(DirectoryTreeNode node) {
        this.nodes.add(node);
    }

    public DirectoryTreeNode fillDirectoryTree(DirectoryTreeNode node, List<DirectoryTreeNode> seenNodes) {

        List<Path> dirs = new ArrayList<>();
        Path folder = Paths.get(node.getFullPath());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, directoryFilter)) {
            for (Path entry : stream) {
                dirs.add(entry);
            }
        } catch (IOException e) {
            log.warn("Directory does not exist: " + e.getMessage());
            return node;
        }

        if (dirs.size() > 0) {
            node.nodes = new ArrayList<>();
            for (Path child : dirs) {
                Path realPath = null;
                try {
                    realPath = child.toRealPath();
                } catch (IOException e) {
                    log.error("Error determining real path: " + e.getMessage());
                }
                if (realPath != null) {
                    DirectoryTreeNode realChildNode = new DirectoryTreeNode(realPath.toFile()); // resolve symbolic link to real path

                    if (seenNodes.contains(realChildNode)) {
                        if (Files.isSymbolicLink(child)) {
                            log.warn("Omitting duplicate symbolically linked directory: " + child.toAbsolutePath() + " --> " + realPath);
                        } else {
                            log.warn("Omitting previously seen directory: " + child.toAbsolutePath() + " --> " + realPath);
                        }
                        continue;
                    }

                    if (Files.isSymbolicLink(child)) {
                        log.info("Adding symbolically linked directory: " + child.toAbsolutePath() + " --> " + realPath);
                    }

                    // only keep track of the nodes we've seen in this branch of the file tree;
                    // it's okay for two separate branches to have the same symbolic links as long as there are no cycles
                    List<DirectoryTreeNode> seenNodesCopy = new ArrayList<>(seenNodes);
                    seenNodesCopy.add(realChildNode);

                    node.addNode(fillDirectoryTree(new DirectoryTreeNode(child.toFile()), seenNodesCopy)); // use absolute path
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

        return node;
    }

    public static DirectoryTreeNode find(DirectoryTreeNode node, String fullPath) {
        if (node.getFullPath().equals(fullPath)) return node;
        if (node.nodes != null) {
            DirectoryTreeNode found;
            for (DirectoryTreeNode child : node.nodes) {
                if ((found = find(child, fullPath)) != null)
                    return found;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DirectoryTreeNode) {
            DirectoryTreeNode other = (DirectoryTreeNode) obj;
            return fullPath.equals(other.getFullPath());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
}	
