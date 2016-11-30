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

package org.mitre.mpf.audioVideo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class MPFEncoder {

    private static final Logger LOG = LoggerFactory.getLogger(MPFEncoder.class);

    public MPFEncoder() {}

    public Float getFramesPerSecond(File source) {

        MPF_FFMPEGExecutor ffmpeg = new MPF_FFMPEGExecutor();
        Float returnVal = null;
        String query = "ffmpeg -i " + source.toString() + " 2>&1 | sed -n 's/.*, \\(.*\\) fp.*/\\1/p'";
        LOG.debug("query = " + query);
        String[] command = {"/bin/sh", "-c", query};
        Process process = null;
        InputStreamReader stdInputReader= null;
        InputStreamReader stdErrorReader = null;
        BufferedReader stdInput=null;
        BufferedReader stdError = null;
        try {
            process = ffmpeg.executeCustomCommand(command);
            stdInputReader = new InputStreamReader(process.getInputStream());
            stdInput = new BufferedReader(stdInputReader);
            stdErrorReader = new InputStreamReader(process.getErrorStream());
            stdError = new BufferedReader(stdErrorReader);

            LOG.debug("The ffmpeg standard output is:");
            String s;
            while ((s = stdInput.readLine()) != null) {
                LOG.debug(s);
                returnVal = Float.parseFloat(s);
            }

            // read any errors from the attempted command
            LOG.debug("The ffmpeg standard error output is:\n");
            while ((s = stdError.readLine()) != null) {
                LOG.debug(s);
            }
        } catch (IOException e) {
            LOG.error("Failed to obtain the fps in the file '{}' due to an exception.", source, e);
        } finally {
            if (process!=null) {
                process.destroy();
            }
            if (stdInputReader != null) {
                try {
                    stdInputReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdErrorReader != null) {
                try {
                    stdErrorReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdInput != null) {
                try {
                    stdInput.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdError != null) {
                try {
                    stdError.close();
                } catch (Throwable t) {
                    ;
                }
            }
        }

        return returnVal;
    }

    public Integer getDuration(File source) { // TODO: needs to be tested

        MPF_FFMPEGExecutor ffmpeg = new MPF_FFMPEGExecutor();
        Integer returnVal = null;

        String query = "ffmpeg -i " + source.toString() + " 2>&1 | sed -n 's/.*Duration: \\([^ ,]*\\).*/\\1/p'";
        LOG.debug("query = " + query);
        String[] command = {"/bin/sh", "-c", query};

        Process process = null;
        InputStreamReader stdInputReader= null;
        InputStreamReader stdErrorReader = null;
        BufferedReader stdInput=null;
        BufferedReader stdError = null;
        try {
            process = ffmpeg.executeCustomCommand(command);
            stdInputReader = new InputStreamReader(process.getInputStream());
            stdInput = new BufferedReader(stdInputReader);
            stdErrorReader = new InputStreamReader(process.getErrorStream());
            stdError = new BufferedReader(stdErrorReader);


            String s;
            while ((s = stdInput.readLine()) != null) {
                String[] tokens = s.split(":");

                int hours = Integer.parseInt(tokens[0]);
                int minutes = Integer.parseInt(tokens[1]);

                String[] subtokens = tokens[2].split("\\.");
                int seconds = Integer.parseInt(subtokens[0]);
                int milliseconds = Integer.parseInt(subtokens[1]);

                returnVal = (hours * 60 * 60 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + milliseconds;

                System.out.println("Converted " + s + " to " + returnVal);
            }

            // read any errors from the attempted command
            LOG.debug("The ffmpeg standard error output is:\n");
            while ((s = stdError.readLine()) != null) {
                LOG.debug(s);
            }
        } catch (IOException e) {
            LOG.error("Failed to obtain the duration in the file '{}' due to an exception.", source, e);
        } catch (NumberFormatException e) {
            LOG.error("Failed to obtain the duration in the file '{}' due to an exception.", source, e);
        } finally {
            if (process!=null) {
                process.destroy();
            }
            if (stdInputReader != null) {
                try {
                    stdInputReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdErrorReader != null) {
                try {
                    stdErrorReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdInput != null) {
                try {
                    stdInput.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdError != null) {
                try {
                    stdError.close();
                } catch (Throwable t) {
                    ;
                }
            }
        }

        return returnVal;
    }

    // NOTE: Using tbr may cause problems:
    // "tbr is guessed from the video stream and is the value users want to see when they look for the video frame rate,
    // except sometimes it is twice what one would expect because of field rate versus frame rate."

    public Float getTbr(File source) {

        MPF_FFMPEGExecutor ffmpeg = new MPF_FFMPEGExecutor();
        Float returnVal = null;
        String query = "ffmpeg -i " + source.toString() + " 2>&1 | sed -n 's/.*, \\(.*\\) tbr.*/\\1/p'";
        LOG.debug("query = " + query);
        String[] command = {"/bin/sh", "-c", query};

        Process process = null;
        InputStreamReader stdInputReader= null;
        InputStreamReader stdErrorReader = null;
        BufferedReader stdInput=null;
        BufferedReader stdError = null;
        try {
            process = ffmpeg.executeCustomCommand(command);
            stdInputReader = new InputStreamReader(process.getInputStream());
            stdInput = new BufferedReader(stdInputReader);
            stdErrorReader = new InputStreamReader(process.getErrorStream());
            stdError = new BufferedReader(stdErrorReader);
            LOG.debug("The ffmpeg standard output is:");
            String s;
            while ((s = stdInput.readLine()) != null) {
                LOG.debug(s);
                returnVal = Float.parseFloat(s);
            }
            // read any errors from the attempted command
            LOG.debug("The ffmpeg standard error output is:\n");
            while ((s = stdError.readLine()) != null) {
                LOG.debug(s);
            }
        } catch (IOException e) {
            LOG.error("Failed to obtain the tbr in the file '{}' due to an exception.", source, e);
        } finally {
            if (process!=null) {
                process.destroy();
            }
            if (stdInputReader != null) {
                try {
                    stdInputReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdErrorReader != null) {
                try {
                    stdErrorReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdInput != null) {
                try {
                    stdInput.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdError != null) {
                try {
                    stdError.close();
                } catch (Throwable t) {
                    ;
                }
            }
        }

        return returnVal;
    }

    public void transcodeWithFiltering(File source, File target, MPFEncodingAttributes attributes)
            throws IllegalArgumentException, IOException {

        target = target.getAbsoluteFile();
        MPF_FFMPEGExecutor ffmpeg = new MPF_FFMPEGExecutor();

        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
            LOG.error("Failed to create temporary audio file for processing");
            return;
        }

        // ffmpeg –i <input file> -ss offset -t duration -ac 1 -ar 16000 –acodec pcm_s16le –af “highpass=f=200, lowpass=f=3000" -vn -f wav -y <output file>.wav
        String query = "ffmpeg -i " + source.getAbsolutePath().toString();
        if (attributes.getOffset() != null) {
            query += " -ss " + String.valueOf(attributes.getOffset().floatValue());
        }
        if (attributes.getDuration() != null) {
            query += " -t " + String.valueOf(attributes.getDuration().floatValue());
        }
        query += " -ac " + String.valueOf(attributes.getAudioAttributes().getChannels().intValue()) +
            " -ar " + String.valueOf(attributes.getAudioAttributes().getSamplingRate().intValue()) +
            " -acodec " + attributes.getAudioAttributes().getCodec() +
            " -af \"highpass=f=" + String.valueOf(attributes.getAudioAttributes().getHighpassCutoffFrequency().intValue()) +
            ", lowpass=f=" + String.valueOf(attributes.getAudioAttributes().getLowpassCutoffFrequency().intValue()) + "\"" +
            " -vn " + "-f " + attributes.getFormat() + " -y " + target.getAbsolutePath().toString() +
            " -loglevel error"; // by default, ffmpeg writes all of its messages to stderr,
                                  // so we set the loglevel to get just errors
        LOG.debug("query = " + query);
        String[] command = {"/bin/sh", "-c", query};

        Process process = null;
        InputStreamReader stdInputReader= null;
        InputStreamReader stdErrorReader = null;
        BufferedReader stdInput=null;
        BufferedReader stdError = null;
        try {
            process = ffmpeg.executeCustomCommand(command);
            stdInputReader = new InputStreamReader(process.getInputStream());
            stdInput = new BufferedReader(stdInputReader);
            stdErrorReader = new InputStreamReader(process.getErrorStream());
            stdError = new BufferedReader(stdErrorReader);
            // read the output from the command
            if(LOG.isDebugEnabled()) {
                LOG.debug("The ffmpeg standard output is:");
                String sStd;
                while ((sStd = stdInput.readLine()) != null) {
                    LOG.debug(sStd+'\n');
                }
            }
            // read any errors from the attempted command
            StringBuilder stringBuilder = new StringBuilder();
            for (String line = stdError.readLine(); line != null; line = stdError.readLine()) {
                stringBuilder.append(line+'\n');
            }
            if(stringBuilder.length() > 0) {
                LOG.warn("FFMPEG STDERR Output:\n{}", stringBuilder.toString());
            }
        } finally {
            if (process!=null) {
                process.destroy();
            }
            if (stdInputReader != null) {
                try {
                    stdInputReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdErrorReader != null) {
                try {
                    stdErrorReader.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdInput != null) {
                try {
                    stdInput.close();
                } catch (Throwable t) {
                    ;
                }
            }
            if (stdError != null) {
                try {
                    stdError.close();
                } catch (Throwable t) {
                    ;
                }
            }
        }
        if(!target.exists() || target.length() == 0) {
            throw new IOException("Unable to transcode input file: "+source.getAbsolutePath());
        }
    }

}
