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
package org.mitre.mpf.nms;

import org.mitre.mpf.nms.xml.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Simple {@link BaseServiceLauncher} that just sends a {@literal q\n} on STDIN
 * to tell the subprocess to shutdown.
 */
public class SimpleServiceLauncher extends GenericServiceLauncher {

    /**
     * @param desc
     */
    public SimpleServiceLauncher(ServiceDescriptor desc) {
        super(desc);
    }

    // moved this into the XML!
    /**
     * Required override in derived classes. This can be stubbed out if nothing
     * needs to be done. Useful possibilities include sending something via
     * STDIN to the app to tell it to shutdown gracefully followed by some sleep
     * if needed. Upon return the caller will terminate the process.
     *
     * @Override public void additionalProcessPreconfig(ProcessBuilder pb) { //
     * TODO Verify Implementation Map<String, String> env = pb.environment();
     * String command = pb.command().get(0); command = command.substring(0,
     * command.lastIndexOf('/')); env.put("LD_LIBRARY_PATH",
     * env.get("LD_LIBRARY_PATH") + ":" + command + "/../lib"); }
     */
    /**
     * Required override in derived classes. This can be stubbed out if nothing
     * needs to be done. Useful possibilities include sending something via
     * STDIN to the app to tell it to shutdown gracefully followed by some sleep
     * if needed. Upon return the caller will terminate the process.
     
    @Override
    public void sendShutdownToApp() {
        // TODO Verify Implementation
        LOG.info("SHUTTING DOWN {}", this.getServiceName());
        sendLine("q\n");
        // should we wait for something returned?
    }*/

    @Override
    public void started(OutputStream input, InputStream output, InputStream error) {
        // TODO Verify Implementation
    }

    /**
     * Returns the the full command to execute with current AMQ host inserted as
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
        for (String arg : s.getArgs()) {
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
