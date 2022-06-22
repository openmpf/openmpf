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
package org.mitre.mpf.nms;

import org.mitre.mpf.nms.util.EnvironmentVariableExpander;
import org.mitre.mpf.nms.json.EnvironmentVariable;
import org.mitre.mpf.nms.json.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Abstract base launcher.
 * <br/>
 * Environmental Variables Set:
 * <pre>
 * ACTIVE_MQ_BROKER_URI given by ServiceDescriptor
 * SERVICE_NAME given by ServiceDescriptor (FQN)
 * </pre> Also processes any given in by the {
 *
 * @linke Service}
 */
public abstract class BaseServiceLauncher implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(BaseServiceLauncher.class);

    // constants
    private final static int DEFAULT_RESTART_WAIT = 2000; // milliseconds
    private final static int DEFAULT_SHUTDOWN_WAIT = 5000; // 5 seconds  (for legitimacy, keep at 1000 or more)
    private final static int DEFAULT_MIN_TIME_UP_MILLISECONDS = 60 * 1000; //60 seconds

    // program related
    private ServiceDescriptor mServiceDesc = null;
    // protected String        nodeName = null;    // arbitrary name of node, informational only
    // protected String        programPath = null; // path to child application to be run
    //private String mCommand = null;        // command line args to pass to child app

    // child process
    protected ProcessBuilder pb;
    protected Process child;

    private int restarts = 0;    // increment every time we have to restart the task because of failure
    private boolean m_fatalProblemFlag = true;  // set to true until certain criteria are met

    // I/O streams to/from child app
    private OutputStream stdIn = null;
    private InputStream stdOut = null;
    private InputStream stdErr = null;

    protected boolean autoDrainStdout = true;
    protected boolean autoDrainStdIn = true;

    // program state and options
    private boolean isRunning = false;
    private boolean mRunToCompletion = false;
    private boolean mIsShutdown = false;
    private boolean restartOnFailure = true;
    private int mExitStatus = 0;

    // class thread that starts and watches child app
    private Thread thread = null;

    // configurables
    protected int restartWaitMillis = DEFAULT_RESTART_WAIT; // default is 2 seconds
    protected int shutdownWaitMillis = DEFAULT_SHUTDOWN_WAIT;
    private int minTimeUpMilliSeconds = DEFAULT_MIN_TIME_UP_MILLISECONDS; //this is set by a property in the node manager and passed in

    private OutputShredder outShredder;
    private OutputShredder errShredder;
    private OutputReader outReader = null;
    private OutputReceiver outReceiver = null;
    private OutputReceiver errReceiver = null;

    public static BaseServiceLauncher getLauncher(ServiceDescriptor desc) {

        final String launcher = desc.getService().getLauncher();
        if (null == launcher || launcher.isEmpty() || "generic".equals(launcher)) {
            // default
            return new GenericServiceLauncher(desc);
        } else if ("simple".equals(launcher)) {
            return new SimpleServiceLauncher(desc);
        }
        LOG.warn("Unknown launcher: {}", launcher);
        return null;
    }

    /**
     * Launch Service.
     * <p/>
     * STDOUT and STDERR are not redirected unless Logging level is DEBUG.
     *
     * @param desc Service Descriptor to launch
     */
    public BaseServiceLauncher(ServiceDescriptor desc) {
    	LOG.debug("Base Service Launcher '{}' created", desc.getService().getName());
    	restarts = desc.getRestarts();

        this.mServiceDesc = desc;

        if (LOG.isDebugEnabled()) {
            // redirects to LOG debug
            this.outReceiver = new OutputReceiver() {

                @Override
                public void receiveOutput(String outputName, String output) {
                    LOG.debug("Node [{}]: {}", mServiceDesc.getService().getName(), output);
                }

            };

            this.errReceiver = new OutputReceiver() {

                @Override
                public void receiveOutput(String outputName, String output) {
                    LOG.debug("Node stderr [{}]: {}", mServiceDesc.getService().getName(), output);
                }

            };
        } // else don't waste time and drop any input from the service (see OutputShredder)
    }

    /**
     * Splits a Service argument on spaces, but not escaped spaces.
     * <pre>
     * "this\ is\ escaped\ spaces"
     * </pre>
     */
    public String[] splitArguments(String arg) {
        return arg.split("(?<!\\\\) "); // look around assertion
    }

    // Return the number of times this service was restarted internally
    public int getRestartCount() {
        return restarts;
    }

    public boolean getFatalProblemFlag() {
        return m_fatalProblemFlag;
    }

    public boolean isRunning() {
        return (null != this.child && this.isRunning);
    }

    public boolean runToCompletion() {
        return this.mRunToCompletion;
    }

    // full set of arguments that will be passed to child when started
    /*public void setArgs(String args) {
     this.args = programPath + " " + args;
     }
     public  void setoReader(OutputReader outReader) {
     this.outReader = outReader;
     }

     public void setoReceiver(OutputReceiver outReceiver) {
     this.outReceiver = outReceiver;
     }*/

    /**
     *
     * @param minServiceTimeupMillis the minimum amount of time a service must be running to allow more than one restart
     * @return
     */
    public boolean startup(int minServiceTimeupMillis) {
    	this.minTimeUpMilliSeconds = minServiceTimeupMillis;

        // Until shown otherwise, we have had a problem starting up
        m_fatalProblemFlag = true;

        File tester = new File(this.getCommandPath());
        String fullPath = null;

        // get fully qualified path to executable, completing any relative paths used.
        try {
            fullPath = tester.getCanonicalPath();
        } catch (IOException e) {
            LOG.error("Exception", e);
        }

        if (!tester.exists()) {
            LOG.error("Node service command '{}' for '{}' does not exist or is not reachable.", fullPath, this.mServiceDesc.getFullyQualifiedName());
            mRunToCompletion = true;
            return false;
        } else if (!tester.canExecute()) {
            LOG.error("Node service command '{}' for '{}' exists but is not executable.  Check permissions!", fullPath, this.mServiceDesc.getFullyQualifiedName());
            mRunToCompletion = true;
            return false;
        }

        thread = new Thread(this);
        thread.start();

        return true;
    }

    public void shutdown() {
        if (!isRunning) {
            return;
        }
        mIsShutdown = true;
        //if called to shutdown we don't want to allow a restart! - only restart on failure
        restartOnFailure = false;
        sendShutdownToApp();  // user-defined shutdown method
        OutputShredder shredder = this.getStdOutShredder();
        boolean status = false;
        if (null != shredder) {
            try {
                // wait until stdout from the child closes
                status = shredder.waitTillDone(this.shutdownWaitMillis);
            } catch (InterruptedException ie) {
                LOG.warn("InterruptedException encountered when shutting down {} ", this.getServiceName());
                Thread.currentThread().interrupt();
            }
            if (status) {
                LOG.debug("Service {} shutdown", this.getServiceName());
            } else {
                LOG.warn("Failed to properly shutdown {} in {} millsec", this.getServiceName(), this.shutdownWaitMillis);
            }
        } else {
            LOG.info("No StdOut Shredder associated with service");
        }
        // Wait shutdownWaitMillis for thread to finish on its own (checking every 1% of that)
        // Makes little sense if shutdownWaitMillis is really small (e.g. under 1000 ms)
        if (!status || isRunning) {
            int loop = 100;
            while (isRunning && loop-- > 0) {
                try {
                    Thread.sleep(shutdownWaitMillis / 100);
                } catch (InterruptedException e) {
                    LOG.error("Exception", e);
                }
            }
        }

        // we waited, if still running then shut it down
        if (isRunning) {
            child.destroy();
        }
    }

    public ServiceDescriptor getService() {
        return this.mServiceDesc;
    }

    public String getServiceName() {
        return this.mServiceDesc.getFullyQualifiedName();
    }

    // provide access to the I/O of the app
    public OutputStream getStdIn() {
        return stdIn;
    }

    public InputStream getStdOut() {
        return stdOut;
    }

    public InputStream getStdErr() {
        return stdErr;
    }

    public OutputShredder getStdOutShredder() {
        return this.outShredder;
    }

    public OutputShredder getStdErrShredder() {
        return this.errShredder;
    }

    public void setStdOutReceiver(OutputReceiver receiver) {
        this.outReceiver = receiver;
    }

    public void setStdErrReceiver(OutputReceiver receiver) {
        this.errReceiver = receiver;
    }

    public int sendLine(String toInput) {
        if (stdIn == null) {
            return -1;
        }

        try {
            stdIn.write(toInput.getBytes());
            stdIn.flush();
        } catch (IOException e) {
            LOG.error("Exception", e);
            return -1;
        }

        return 0;
    }

    private OutputShredder getStdOutShredder(InputStream stream) {
        return new OutputShredder(stream, "stdout", outReader, outReceiver);
    }

    /**
     * Used in building the process.
     *
     * @param stream
     * @return
     */
    private OutputShredder getStdErrShredder(InputStream stream) {
        return new OutputShredder(stream, "stderr", outReader, errReceiver);
    }

    /**
     * Main thread for this class. It's purpose is simple, run the node
     * application and wait around for it to die. If the app is to automatically
     * restart after dying then we'll do that too. This thread is initiated by
     * the startup() method.
     */
    public void run() {
        isRunning = true;
        String command[] = this.getCommand();

        do {

            try {
                // start the node process
                pb = new ProcessBuilder(command);

                // check to see if we change working directory
                final Service s = mServiceDesc.getService();
                if (s.getWorkingDirectory() != null && !s.getWorkingDirectory().isEmpty()) {
                    File cwd = new File(this.substituteVariables(s.getWorkingDirectory().trim()));
                    if (cwd.isDirectory()) {
                        LOG.debug("Switching to working directory {}", cwd);
                        pb.directory(cwd);
                    } else {
                        LOG.warn("Unable to switch to working directory {}", cwd);
                        mRunToCompletion = true;
                        throw new IOException(cwd + " is not a directory or does not exist");
                    }
                }

                // add base env var
                Map<String, String> env = pb.environment();
                env.put("ACTIVE_MQ_BROKER_URI", this.mServiceDesc.getActiveMqHost());
                env.put("SERVICE_NAME", this.mServiceDesc.getFullyQualifiedName());
                env.put("COMPONENT_NAME", this.mServiceDesc.getComponentName());
                // add any given by the service
                for (EnvironmentVariable envVar : s.getEnvVars()) {
                    if (null != envVar.getSep() && !envVar.getSep().isEmpty()) {
                        String subst_value = this.substituteVariables(envVar.getValue());
                        envVar.setValue(subst_value);
                        LOG.debug("Appending environment variable {} = {} using {} ", envVar.getKey(), envVar.getValue(), envVar.getSep());
                        if (env.containsKey(envVar.getKey())) {
                            env.put(envVar.getKey(), env.get(envVar.getKey()) + envVar.getSep() + envVar.getValue());
                        } else {
                            env.put(envVar.getKey(), envVar.getValue());
                        }
                    } else {
                        String subst_value = this.substituteVariables(envVar.getValue());
                        envVar.setValue(subst_value);
                        LOG.debug("Adding environment variable {} = {}", envVar.getKey(), envVar.getValue());
                        env.put(envVar.getKey(), envVar.getValue());
                    }
                }

                // derived special configuration for the process environment allowed here
                additionalProcessPreconfig(pb, mServiceDesc);
                //System.out.println(Arrays.toString(pb.command().toArray(new String[]{})));
                child = pb.start();

                // Normally, the children are shutdown from external requests in tune with how they
                // were created.  But, if we get a "sudo service node-manager-stop", all bets are off.
                // We don't want to leave orphaned child processes that will get in the way of future
                // launches.  We have to make a last ditch effort to clean up processes here.
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        if (child != null) {
                            child.destroy();
                            LOG.debug("Child process shutdown: " + s.getCmdPath());
                        }
                    }
                });

                //store the time started to check time up when service shuts down with an exit code
                this.getService().setStartTimeMillis(System.currentTimeMillis());
                LOG.debug("Node service {} started", this.mServiceDesc.getFullyQualifiedName());

                // capture I/O streams
                stdOut = child.getInputStream();
                stdIn = child.getOutputStream();
                stdErr = child.getErrorStream();

                // shredder have their own threads for digesting stdout/stderr, collected data sent to receiver
                // reader and/or receiver may be null in which case shredder will take default action to ensure
                // output stream is drained properly
                outShredder = this.getStdOutShredder(stdOut);
                errShredder = this.getStdErrShredder(stdErr);

                // Wait forever until the process is completed or dies  If we get this far, at least it was
                // able to launch to some degree.
                m_fatalProblemFlag = false;
                mExitStatus = child.waitFor();
                if (restartOnFailure) {
                    Thread.sleep(restartWaitMillis); // pause before restarting if applicable
                }

                // turn out output processing now that child has exited
                outShredder.stop();
                outShredder.getThread().join();
                errShredder.stop();
                errShredder.getThread().join();

                boolean normalShutdown = false;
                switch (mExitStatus) {
                    case 0:
                    	normalShutdown = true;
                        LOG.debug("Service {} exited normally", this.mServiceDesc.getFullyQualifiedName());
                        break;
                    case 143:
                        LOG.warn("Service {} exited with error {} (SIGTERM)", this.mServiceDesc.getFullyQualifiedName(), mExitStatus);
                        break;
                    default:
                        LOG.warn("Service {} exited with error {}", this.mServiceDesc.getFullyQualifiedName(), mExitStatus);
                }

                //should only be trying to restart if not a normal shutdown and not sent a command to stop. restartOnFailure = false when command set!
                if(!normalShutdown && restartOnFailure) {
                	if(restarts > 0) {
	                    long diffMillis = System.currentTimeMillis() - this.getService().getStartTimeMillis();
	                    LOG.warn("Service {} has been running for {} milliseconds before an exit error.", this.mServiceDesc.getFullyQualifiedName(), diffMillis);
	                    if(diffMillis < minTimeUpMilliSeconds) {
	                    	//will no longer let this service restart automatically
	                    	restartOnFailure = false;
	                    	LOG.warn("Service {} has failed too quickly and will no longer restart automatically.", this.mServiceDesc.getFullyQualifiedName());
	                    	//we want the m_fatalProblemFlag set to true to allow the node manager to prevent the service from starting
	                    	//when a new config is saved or the workflow manager restarts
	                    	this.m_fatalProblemFlag = true;
	                    } else {
	                    	LOG.info("Service {} has been running long enough to attempt a restart.", this.mServiceDesc.getFullyQualifiedName());
	                    }
                	} else {
                		LOG.warn("Service {} has not been restarted, attempting to restart.", this.mServiceDesc.getFullyQualifiedName());
                	}
                }

                //restarts can now be incremented after they are used in the if statement above
                if (restartOnFailure) {
                    restarts++;
                }
            } catch (InterruptedException e) {
                child.destroy();
            } catch (IOException e) {
                LOG.error("IO Exception: check execution path: {}", this.mServiceDesc.getService().getCmdPath(), e);
                // force exit
                restartOnFailure = false;
            } catch (Exception e) {
                // really can't let this slip
                LOG.error("Unknown issue", e);
                restartOnFailure = false;
            }
        } while (restartOnFailure);

        if (!mIsShutdown) {
            // we crashed or failed to start
            if (null != child) {
                if (0 != this.mExitStatus) {
                    LOG.warn("Service {} exited abnormally with exit status of {}!", this.mServiceDesc.getFullyQualifiedName(), this.mExitStatus);
                } else {
                    LOG.warn("Service {} exited normally but was not issued a shutdown commmand", this.mServiceDesc.getFullyQualifiedName());
                }
            } else {
                LOG.warn("Failed to start service {}! Check logs", this.mServiceDesc.getFullyQualifiedName());
            }
        } else {
            LOG.debug("Service {} is now fully terminated", this.mServiceDesc.getFullyQualifiedName());
        }
        child = null;

        isRunning = false;
        mRunToCompletion = true;
        LOG.debug("RUN method for service {} ending", this.mServiceDesc.getFullyQualifiedName());
    }

    public String substituteVariables(String str) {
        return EnvironmentVariableExpander.expand(str);
    }

    /**
     * Do variable (string) substitution using environmental variables on the command path
     * @return
     */
    public String getCommandPath() {
        return EnvironmentVariableExpander.expand(mServiceDesc.getService().getCmdPath());
    }

    /**
     * Special configuration for the process environment after BaseNodeLauncher
     * configures the builder.
     *
     * @param pb
     */
    public abstract void additionalProcessPreconfig(ProcessBuilder pb, ServiceDescriptor serviceDescriptor);

    public abstract void sendShutdownToApp();

    /**
     * Returns the actual command line.
     * <br/>
     * The first string should be the program (command) to execute with the
     * remaining strings the argument list. Each argument must be its own
     * string.
     * <pre>
     * "-jar test.jar" -> "-jar", "test.jar"
     * </pre>
     *
     * @return the command
     * @see ProcessBuilder
     */
    public abstract String[] getCommand();

    public abstract void started(OutputStream input, InputStream output, InputStream error);
}
