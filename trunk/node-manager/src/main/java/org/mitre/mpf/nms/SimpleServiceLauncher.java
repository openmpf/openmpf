/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
package org.mitre.mpf.nms;

import org.mitre.mpf.nms.json.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Simple {@link BaseServiceLauncher} that just sends a {@literal q\n} on STDIN
 * to tell the subprocess to shutdown.
 */
public class SimpleServiceLauncher extends GenericServiceLauncher {

    public SimpleServiceLauncher(ServiceDescriptor desc) {
        super(desc);
    }

    /**
     * Special configuration for the process environment after BaseNodeLauncher
     * configures the builder.
     *
     * @param pb
     */
    @Override public void additionalProcessPreconfig(ProcessBuilder pb, ServiceDescriptor serviceDescriptor) {
        // Add $MPF_HOME/lib to the end of ld library path for the C++ component executor process so that it can link
        // with QT, AMQ, and protobuf libs. If ld library path is specified in a component descriptor file's
        // "environmentVariables", it will already be in LD_LIBRARY_PATH before this method is invoked.

        String mpfHomeKey = "MPF_HOME";
        Map<String, String> env = pb.environment();

        String mpfHomeVal = env.get(mpfHomeKey);
        if (mpfHomeVal == null) {
            throw new IllegalStateException("Missing environment variable: " + mpfHomeKey);
        }

        String ldLibPathKey = "LD_LIBRARY_PATH";
        String ldLibPathVal = env.get(ldLibPathKey);
        if (ldLibPathVal != null) {
            env.put(ldLibPathKey, ldLibPathVal + System.getProperty("path.separator") + mpfHomeVal + "/lib");
        } else {
            env.put(ldLibPathKey, mpfHomeVal + "/lib");
        }

        String pythonPathKey = "PYTHONPATH";
        String pythonPathVal = env.get(pythonPathKey);
        String venvSitePackages = mpfHomeVal + "/plugins/" + serviceDescriptor.getService().name() + "/venv/lib/python3.8/site-packages";
        if (pythonPathVal != null) {
            env.put(pythonPathKey, pythonPathVal + System.getProperty("path.separator") + venvSitePackages);
        } else {
            env.put(pythonPathKey, venvSitePackages);
        }
    }

    /**
     * Returns the full command to execute with current AMQ host inserted as
     * the first argument.
     *
     * @return
     */
    @Override
    public String[] getCommand() {
        ArrayList<String> cmd = new ArrayList<String>();
        Service s = this.getService().getService();
        cmd.add(this.getCommandPath());     // do substitutions
        cmd.add(this.getService().getActiveMqHost());
        for (String arg : s.args()) {
            // don't put in empty args: aka <arg></arg>
            if (null == arg || arg.isEmpty()) {
                continue;
            }
            String new_args = this.substituteVariables(arg);
            cmd.addAll(Arrays.asList(this.splitArguments(new_args)));
        }
        return cmd.toArray(new String[]{});
    }
}
