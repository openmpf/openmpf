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

package org.mitre.mpf.nms;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.mitre.mpf.nms.xml.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic {@link BaseServiceLauncher} that just sends a newline on STDIN to tell the subprocess to shutdown.
 */
public class GenericServiceLauncher extends BaseServiceLauncher  {

	private static final Logger LOG = LoggerFactory.getLogger(GenericServiceLauncher.class);
	
    public GenericServiceLauncher(ServiceDescriptor desc) {
        super(desc);
    }

    /**
     * Required override in derived classes.  This can be stubbed out if nothing needs to be done.
     * Useful possibilities include sending something via STDIN to the app to tell it to shutdown gracefully
     * followed by some sleep if needed.  Upon return the caller will terminate the process.
     */
    @Override
    public void additionalProcessPreconfig(ProcessBuilder pb) {
        // TODO Verify Implementation
    }

    /**
     * This doesn't do anything besides sending a newline
     */
    @Override
    public void sendShutdownToApp() {
        LOG.debug("Sending down 'q' to {}", this.getServiceName());
        // processbuilder doesn't give us the pid to send a signal and Windows doesn't have signals.
        this.sendLine("q\n");
        
    }

    @Override
    public void started(OutputStream inStream, InputStream outStream, InputStream errStream) {

    }

    /**
     * Returns the the full command to execute.
     * @return 
     */
    @Override
    public String[] getCommand() {
        ArrayList<String> cmd = new ArrayList<String>();
        Service s = this.getService().getService();
        cmd.add(this.getCommandPath());                 // do substitutions
        for (String arg : s.getArgs()) {
            // don't put in empty args: aka <arg></arg>
            if (null == arg || arg.isEmpty()) {
                continue;
            }
            cmd.addAll(Arrays.asList(this.splitArguments(arg)));
        }
        return cmd.toArray(new String[]{});
    }
}
