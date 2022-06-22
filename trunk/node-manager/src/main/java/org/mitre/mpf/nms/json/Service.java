/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

package org.mitre.mpf.nms.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Backing class for Service (node) elements of a nodeManager element
 *
 * This uses {@link XStreamAlias}, so when building use {@link XStream#processAnnotations}
 */
public class Service  implements Serializable {

    private String cmd;

    private String name;

    private int count = 1;

    private String launcher = "generic";

    private List<String> args = new ArrayList<String>();

    private List<EnvironmentVariable> envVars = new ArrayList<EnvironmentVariable>();

    private String wd = "";

    private String description = "";

    public Service(String name, String cmdPath) {
        this.name = name;
        this.cmd = cmdPath;
    }

    public Service() {
    }

    public String getCmdPath() {
        return cmd;
    }

    /**
     * full canonical path to the command to execute
     * @param path
     */
    public void setCmdPath(String path) {
        this.cmd = path;
    }

    public void setWorkingDirectory(String path) {
        this.wd = path;
    }

    public String getWorkingDirectory() {
        return this.wd;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public void addArg(String arg) {
        this.args.add(arg);
    }

    /**
     * How many to run
     * @return
     */
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLauncher() {
        return this.launcher;
    }

    public void setLauncher(String launcher) {
        this.launcher = launcher;
    }

    public List<EnvironmentVariable> getEnvVars() {
        if (null == this.envVars) {
            return new ArrayList<EnvironmentVariable>();
        }
        return this.envVars;
    }

    public void setEnvVars(List<EnvironmentVariable> vars) {
        this.envVars = vars;
    }

    @JsonIgnore
    public String getArgumentsString() {
      return Arrays.toString(this.args.toArray(new String[]{}));
    }
}
