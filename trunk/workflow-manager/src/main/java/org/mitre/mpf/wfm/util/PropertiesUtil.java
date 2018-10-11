/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.util;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.io.IOUtils;
import org.h2.util.StringUtils;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.transients.TransientDetectionSystemProperties;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.EnvVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.stream.Collectors.*;

@Component(PropertiesUtil.REF)
@Monitored
public class PropertiesUtil {

    private static final Logger log = LoggerFactory.getLogger(PropertiesUtil.class);
    public static final String REF = "propertiesUtil";


    @Autowired
    private ApplicationContext appContext;

    @Autowired
    private MpfPropertiesConfigurationBuilder mpfPropertiesConfigBuilder;

    @javax.annotation.Resource(name="mediaTypesFile")
    private FileSystemResource mediaTypesFile;

    private ImmutableConfiguration mpfPropertiesConfig;

    // The set of core nodes will not change while the WFM is running.
    private ImmutableSet<String> coreMpfNodes;


    @PostConstruct
    private void init() throws IOException, WfmProcessingException {

        parseCoreMpfNodes();

        mpfPropertiesConfig = mpfPropertiesConfigBuilder.getCompleteConfiguration();

        if (!mediaTypesFile.exists()) {
            copyResource(mediaTypesFile, getMediaTypesTemplate());
        }

        Set<PosixFilePermission> permissions = new HashSet<>();
        permissions.add(PosixFilePermission.OWNER_READ);
        permissions.add(PosixFilePermission.OWNER_WRITE);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);

        Path share = Paths.get(getSharePath()).toAbsolutePath();
        if ( !Files.exists(share) ) {
            share = Files.createDirectories(share, PosixFilePermissions.asFileAttribute(permissions));
        }

        if ( !Files.exists(share) || !Files.isDirectory(share) ) {
            throw new WfmProcessingException(String.format(
                    "Failed to create the path '%s'. It does not exist or it is not a directory.",
                    share.toString()));
        }

        artifactsDirectory = createOrFail(share, "artifacts", permissions);
        markupDirectory = createOrFail(share, "markup", permissions);
        outputObjectsDirectory = createOrFail(share, "output-objects", permissions);
        remoteMediaCacheDirectory = createOrFail(share, "remote-media", permissions);
        uploadedComponentsDirectory = createOrFail(share, getComponentUploadDirName(), permissions);
        createOrFail(getPluginDeploymentPath().toPath(), "",
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE
                ));

        // create the default models directory, although the user may have set "detection.models.dir.path" to something else
        createOrFail(share, "models", permissions);

        log.info("All file resources are stored within the shared directory '{}'.", share);
        log.debug("Artifacts Directory = {}", artifactsDirectory);
        log.debug("Markup Directory = {}", markupDirectory);
        log.debug("Output Objects Directory = {}", outputObjectsDirectory);
        log.debug("Remote Media Cache Directory = {}", remoteMediaCacheDirectory);
        log.debug("Uploaded Components Directory = {}", uploadedComponentsDirectory);
    }

    private void parseCoreMpfNodes() {
        String coreMpfNodesStr = System.getenv(EnvVar.CORE_MPF_NODES);

        if (StringUtils.isNullOrEmpty(coreMpfNodesStr)) {
            coreMpfNodes = ImmutableSet.of(); // empty set
        } else {
            coreMpfNodes = Arrays.stream(coreMpfNodesStr.split(",")).map(String::trim)
                    .filter(node -> !node.isEmpty()).collect(collectingAndThen(toSet(), ImmutableSet::copyOf));
        }
    }

    private static File createOrFail(Path parent, String subdirectory, Set<PosixFilePermission> permissions)
                throws IOException, WfmProcessingException {
        Path child = parent.resolve(subdirectory);
        if ( !Files.exists(child) ) {
            child = Files.createDirectories(child, PosixFilePermissions.asFileAttribute(permissions));
        }

        if ( !Files.exists(child) || !Files.isDirectory(child) ) {
            throw new WfmProcessingException(String.format("Failed to create the path '%s'. It does not exist or it is not a directory.", child));
        }

        return child.toAbsolutePath().toFile();
    }

    public String lookup(String propertyName) {
        return mpfPropertiesConfig.getString(propertyName);
    }

    public void setAndSaveCustomProperties(List<PropertyModel> propertyModels) {
        mpfPropertiesConfig = mpfPropertiesConfigBuilder.setAndSaveCustomProperties(propertyModels);
    }

    /**
     * Returns a updated list of property models. Each element contains current value,
     * as well as a flag indicating whether or not a WFM restart is required to apply the change.
     * @return Updated list of property models.
     */
    public List<PropertyModel> getCustomProperties() {
        return mpfPropertiesConfigBuilder.getCustomProperties();
    }

    /**
     * Returns a updated list of property models for the set of immutable properties. Each element contains current value,
     * as well as a flag indicating whether or not a WFM restart is required to apply the change.
     * @return Updated list of property models for the set of immutable properties.
     */
    public List<PropertyModel> getImmutableCustomProperties() {
        return getCustomProperties().stream()
                .filter(pm -> !MpfPropertiesConfigurationBuilder.isMutableProperty(pm.getKey()))
                .collect(toList());
    }

    /**
     * Returns a updated list of property models for the set of mutable properties. Each element contains current value,
     * as well as a flag indicating whether or not a WFM restart is required to apply the change.
     * @return Updated list of property models for the set of mutable properties.
     */
    public List<PropertyModel> getMutableCustomProperties() {
        return getCustomProperties().stream()
                .filter(pm -> MpfPropertiesConfigurationBuilder.isMutableProperty(pm.getKey()))
                .collect(toList());
    }

    public TransientDetectionSystemProperties createDetectionSystemPropertiesSnapshot() {
        Map<String, String> detMap = new HashMap<>();
        mpfPropertiesConfig.getKeys().forEachRemaining(key -> {
            if (MpfPropertiesConfigurationBuilder.isDetectionProperty(key)) {
                detMap.put(key, mpfPropertiesConfig.getString(key)); // resolve final value
            }
        } );
        return new TransientDetectionSystemProperties(Collections.unmodifiableMap(detMap));
    }

    //
    // JMX configuration
    //

    public boolean isAmqBrokerEnabled() {
        return mpfPropertiesConfig.getBoolean("jmx.amq.broker.enabled");
    }

    public String getAmqBrokerJmxUri() {
        return mpfPropertiesConfig.getString("jmx.amq.broker.uri");
    }

    public String getAmqBrokerAdminUsername() {
        return mpfPropertiesConfig.getString("jmx.amq.broker.admin.username");
    }

    public String getAmqBrokerAdminPassword() {
        return mpfPropertiesConfig.getString("jmx.amq.broker.admin.password");
    }

    public Set<String> getAmqBrokerPurgeWhiteList() {
        return new HashSet<>(mpfPropertiesConfig.getList(String.class, "jmx.amq.broker.whiteList"));
    }

    //
    // Main configuration
    //

    public String getSiteId() {
        return mpfPropertiesConfig.getString("output.site.name");
    }

    public boolean isOutputObjectsEnabled() {
        return mpfPropertiesConfig.getBoolean("mpf.output.objects.enabled");
    }

    public boolean isOutputQueueEnabled() {
        return mpfPropertiesConfig.getBoolean("mpf.output.objects.queue.enabled");
    }

    public String getOutputQueueName() {
        return mpfPropertiesConfig.getString("mpf.output.objects.queue.name");
    }

    public String getSharePath() {
        return mpfPropertiesConfig.getString("mpf.share.path");
    }

    private File artifactsDirectory;
    public File getArtifactsDirectory() { return artifactsDirectory; }

    public File getJobArtifactsDirectory(long jobId) {
        return new File(artifactsDirectory, String.valueOf(jobId));
    }

    public File createArtifactDirectory(long jobId, long mediaId, int stageIndex) throws IOException {
        Path path = Paths.get(artifactsDirectory.toURI()).resolve(String.format("%d/%d/%d", jobId, mediaId, stageIndex)).normalize().toAbsolutePath();
        Files.createDirectories(path);
        return path.toFile();
    }
    public File createArtifactFile(long jobId, long mediaId, int stageIndex, String name) throws IOException {
        Path path = Paths.get(artifactsDirectory.toURI()).resolve(String.format("%d/%d/%d/%s", jobId, mediaId, stageIndex, name)).normalize().toAbsolutePath();
        Files.createDirectories(path.getParent());
        return path.toFile();
    }

    private File outputObjectsDirectory;
    /** Gets the path to the top level output object directory
     * @return path to the top level output object directory
     */
    public File getOutputObjectsDirectory() { return outputObjectsDirectory; }

    public File getJobOutputObjectsDirectory(long jobId) {
        return new File(outputObjectsDirectory, String.valueOf(jobId));
    }

    /** Create the output objects directory and detection*.json file for batch jobs
     * @param jobId unique id that has been assigned to the batch job
     * @return directory that was created under the output objects directory for storage of detection files from this batch job
     * @throws IOException
     */
    public File createDetectionOutputObjectFile(long jobId) throws IOException {
        return createOutputObjectsFile(jobId, "detection");
    }

    /** Create the output objects directory for a job
     * Note: this method is typically used by streaming jobs.
     * The WFM will need to create the directory before it is populated with files.
     * @param jobId unique id that has been assigned to the job
     * @return directory that was created under the output objects directory for storage of files from this job
     * @throws IOException
     */
    public File createOutputObjectsDirectory(long jobId) throws IOException {
        String fileName = String.format("%d", jobId);
        Path path = Paths.get(outputObjectsDirectory.toURI()).resolve(fileName).normalize().toAbsolutePath();
        Files.createDirectories(path);
        return path.toFile();
    }

    /** Create the output object file in the specified streaming job output objects directory
     * @param time the time associated with the job output
     * @param parentDir this streaming job's output objects directory
     * @return output object File that was created under the specified output objects directory
     * @throws IOException
     */
    public File createStreamingOutputObjectsFile(LocalDateTime time, File parentDir) throws IOException {
        String fileName = String.format("summary-report %s.json", TimeUtils.getLocalDateTimeAsString(time));
        Path path = Paths.get(parentDir.toURI()).resolve(fileName).normalize().toAbsolutePath();
        Files.createDirectories(path.getParent());
        return path.toFile();
    }

    /** Create the File to be used for storing output objects from a job, plus create the directory path to that File
     * @param jobId unique id that has been assigned to the job
     * @param outputObjectType pre-defined type of output object for the job
     * @return File to be used for storing an output object for this job
     * @throws IOException
     */
    private File createOutputObjectsFile(long jobId, String outputObjectType) throws IOException {
        return createOutputObjectsFile(jobId,outputObjectsDirectory,outputObjectType);
    }

    /** Create the File to be used for storing output objects from a job, plus create the directory path to that File
     * @param jobId unique id that has been assigned to the job
     * @param parentDir parent directory for the file to be created
     * @param outputObjectType pre-defined type of output object for the job
     * @return File to be used for storing an output object for this job
     * @throws IOException
     */
    private File createOutputObjectsFile(long jobId, File parentDir, String outputObjectType) throws IOException {
        String fileName = String.format("%d/%s.json", jobId, TextUtils.trimToEmpty(outputObjectType));
        Path path = Paths.get(parentDir.toURI()).resolve(fileName).normalize().toAbsolutePath();
        Files.createDirectories(path.getParent());
        return path.toFile();
    }

    private File remoteMediaCacheDirectory;
    public File getRemoteMediaCacheDirectory() { return remoteMediaCacheDirectory; }

    private File markupDirectory;
    public File getMarkupDirectory() { return markupDirectory; }

    public File getJobMarkupDirectory(long jobId) {
        return new File(markupDirectory, String.valueOf(jobId));
    }

    public Path createMarkupPath(long jobId, long mediaId, String extension) throws IOException {
        Path path = Paths.get(markupDirectory.toURI()).resolve(String.format("%d/%d/%s%s", jobId, mediaId,
                UUID.randomUUID(), TextUtils.trimToEmpty(extension))).normalize().toAbsolutePath();
        Files.createDirectories(path.getParent());
        return Files.createFile(path);
    }

    //
    // Detection configuration
    //

    public ArtifactExtractionPolicy getArtifactExtractionPolicy() {
        return mpfPropertiesConfig.get(ArtifactExtractionPolicy.class, "detection.artifact.extraction.policy");
    }

    public int getSamplingInterval() {
        return mpfPropertiesConfig.getInt("detection.sampling.interval");
    }

    public int getFrameRateCap() {
        return mpfPropertiesConfig.getInt("detection.frame.rate.cap");
    }

    public double getConfidenceThreshold() {
        return mpfPropertiesConfig.getDouble("detection.confidence.threshold");
    }

    public int getMinAllowableSegmentGap() {
        return mpfPropertiesConfig.getInt("detection.segment.minimum.gap");
    }

    public int getTargetSegmentLength() {
        return mpfPropertiesConfig.getInt("detection.segment.target.length");
    }

    public int getMinSegmentLength() {
        return mpfPropertiesConfig.getInt("detection.segment.minimum.length");
    }

    public boolean isTrackMerging() {
        return mpfPropertiesConfig.getBoolean("detection.track.merging.enabled");
    }

    public int getMinAllowableTrackGap() {
        return mpfPropertiesConfig.getInt("detection.track.min.gap");
    }

    public int getMinTrackLength() {
        return mpfPropertiesConfig.getInt("detection.track.minimum.length");
    }

    public double getTrackOverlapThreshold() {
        return mpfPropertiesConfig.getDouble("detection.track.overlap.threshold");
    }

    //
    // JMS configuration
    //

    public int getJmsPriority() {
        return mpfPropertiesConfig.getInt("jms.priority");
    }

    //
    // Pipeline configuration
    //

    private FileSystemResource getAlgorithmsData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.algorithms.file"));
    }

    private Resource getAlgorithmsTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.algorithms.template"));
    }

    public WritableResource getAlgorithmDefinitions() {
        return getDataResource(getAlgorithmsData(), getAlgorithmsTemplate());
    }

    private FileSystemResource getActionsData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.actions.file"));
    }

    private Resource getActionsTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.actions.template"));
    }

    public WritableResource getActionDefinitions() {
        return getDataResource(getActionsData(), getActionsTemplate());
    }

    private FileSystemResource getTasksData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.tasks.file"));
    }

    private Resource getTasksTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.tasks.template"));
    }

    public WritableResource getTaskDefinitions() {
        return getDataResource(getTasksData(), getTasksTemplate());
    }

    private FileSystemResource getPipelinesData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.pipelines.file"));
    }

    private Resource getPipelinesTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.pipelines.template"));
    }

    public WritableResource getPipelineDefinitions() {
        return getDataResource(getPipelinesData(), getPipelinesTemplate());
    }

    private FileSystemResource getNodeManagerPaletteData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.nodemanagerpalette.file"));
    }

    private Resource getNodeManagerPaletteTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.nodemanagerpalette.template"));
    }

    public WritableResource getNodeManagerPalette() {
        return getDataResource(getNodeManagerPaletteData(), getNodeManagerPaletteTemplate());
    }

    private FileSystemResource getNodeManagerConfigData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.nodemanagerconfig.file"));
    }

    private Resource getNodeManagerConfigTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.nodemanagerconfig.template"));
    }

    public WritableResource getNodeManagerConfigResource() {
        return getDataResource(getNodeManagerConfigData(), getNodeManagerConfigTemplate());
    }


    private FileSystemResource getStreamingServicesData() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.streamingprocesses.file"));
    }

    private Resource getStreamingServicesTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.streamingprocesses.template"));
    }

    public WritableResource getStreamingServices() {
        return getDataResource(getStreamingServicesData(), getStreamingServicesTemplate());
    }


    //
    // Component upload and registration properties
    //

    private FileSystemResource getComponentInfo() {
        return new FileSystemResource(mpfPropertiesConfig.getString("data.component.info.file"));
    }

    private Resource getComponentInfoTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("data.component.info.template"));
    }

    public WritableResource getComponentInfoFile() {
        return getDataResource(getComponentInfo(), getComponentInfoTemplate());
    }

    private File uploadedComponentsDirectory;
    public File getUploadedComponentsDirectory() {
        return uploadedComponentsDirectory;
    }

    // should not need these outside of this file
    private String getComponentUploadDirName() {
        return mpfPropertiesConfig.getString("component.upload.dir.name");
    }

    public File getComponentDependencyFinderScript() {
        return new File(mpfPropertiesConfig.getString("mpf.component.dependency.finder.script"));
    }

    public File getPluginDeploymentPath() {
        return new File(mpfPropertiesConfig.getString("mpf.plugins.path"));
    }

    public boolean isStartupAutoRegistrationSkipped() {
        String key = "startup.auto.registration.skip.spring";
        try {
            return mpfPropertiesConfig.getBoolean(key, false);
        } catch (ConversionException e) {
            if (mpfPropertiesConfig.getString(key).startsWith("${")) {
                log.warn("Unable to determine value for \"" + key + "\". It may not have been set via Maven. Using default value of \"false\".");
                return false;
            }
            throw e;
        }
    }

    public String getThisMpfNodeHostName() {
        return System.getenv(EnvVar.THIS_MPF_NODE);
    }

    public Set<String> getCoreMpfNodes() {
        return coreMpfNodes;
    }

    //
    // Web settings
    //

    // directory under which log directory is located: <log.parent.dir>/<hostname>/log
    public String getLogParentDir() {
        return mpfPropertiesConfig.getString("log.parent.dir");
    }

    public List<String> getWebActiveProfiles() {
        return mpfPropertiesConfig.getList(String.class, "web.active.profiles");
    }

    public int getWebSessionTimeout() {
        return mpfPropertiesConfig.getInt("web.session.timeout");
    }

    public String getServerMediaTreeRoot() {
        return mpfPropertiesConfig.getString("web.server.media.tree.base");
    }

    public int getWebMaxFileUploadCnt() {
        return mpfPropertiesConfig.getInt("web.max.file.upload.cnt");
    }

    //
    // Version information
    //

    public String getSemanticVersion() {
        return mpfPropertiesConfig.getString("mpf.version.semantic");
    }

    public String getGitHash() {
        return mpfPropertiesConfig.getString("mpf.version.git.hash");
    }

    public String getGitBranch() {
        return mpfPropertiesConfig.getString("mpf.version.git.branch");
    }

    public String getBuildNum() {
        return mpfPropertiesConfig.getString("mpf.version.jenkins.buildnum");
    }

    public String getOutputObjectVersion() {
        return mpfPropertiesConfig.getString("mpf.version.json.output.object.schema");
    }


    public FileSystemResource getMediaTypesFile() {
        return mediaTypesFile;
    }

    private Resource getMediaTypesTemplate() {
        return appContext.getResource(mpfPropertiesConfig.getString("config.mediaTypes.template"));
    }

    public String getAmqUri() {
        return mpfPropertiesConfig.getString("mpf.output.objects.amq.broker.uri");
    }

    //
    // Streaming job properties
    //

    /**
     * Get the health report callback rate, in milliseconds
     * @return health report callback rate, in milliseconds
     */
    public long getStreamingJobHealthReportCallbackRate() {
        return mpfPropertiesConfig.getLong("streaming.healthReport.callbackRate");
    }

    /**
     * Get the streaming job stall alert threshold, in milliseconds
     * @return streaming job stall alert threshold, in milliseconds
     */
    public long getStreamingJobStallAlertThreshold() {
        return mpfPropertiesConfig.getLong("streaming.stallAlert.detectionThreshold");
    }

    //
    // Ansible configuration
    //

    public String getAnsibleChildVarsPath() {
        return mpfPropertiesConfig.getString("mpf.ansible.child.vars.path");
    }

    public String getAnsibleCompDeployPath() {
        return mpfPropertiesConfig.getString("mpf.ansible.compdeploy.path");
    }

    public String getAnsibleCompRemovePath() {
        return mpfPropertiesConfig.getString("mpf.ansible.compremove.path");
    }

    public boolean isAnsibleLocalOnly() {
        return mpfPropertiesConfig.getBoolean("mpf.ansible.local-only", false);
    }

    //
    // Remote media settings
    //

    public int getRemoteMediaDownloadRetries() {
        return mpfPropertiesConfig.getInt("remote.media.download.retries");
    }

    public int getRemoteMediaDownloadSleep() {
        return mpfPropertiesConfig.getInt("remote.media.download.sleep");
    }

    //
    // Node management settings
    //

    public boolean isNodeAutoConfigEnabled() {
        return mpfPropertiesConfig.getBoolean("node.auto.config.enabled");
    }

    public boolean isNodeAutoUnconfigEnabled() {
        return mpfPropertiesConfig.getBoolean("node.auto.unconfig.enabled");
    }

    public int getNodeAutoConfigNumServices() {
        return mpfPropertiesConfig.getInt("node.auto.config.num.services.per.component");
    }

    // Helper methods

    private static WritableResource getDataResource(WritableResource dataResource, InputStreamSource templateResource) {
        if (dataResource.exists()) {
            return dataResource;
        }

        try {
            log.info("{} doesn't exist. Copying from {}", dataResource, templateResource);
            copyResource(dataResource, templateResource);
            return dataResource;
        } catch ( IOException e ) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyResource(WritableResource target, InputStreamSource source) throws IOException {
        createParentDir(target);
        try ( InputStream inStream = source.getInputStream(); OutputStream outStream = target.getOutputStream() ) {
            IOUtils.copy(inStream, outStream);
        }
    }

    public static void createParentDir(Resource resource) throws IOException {
        Path resourcePath = Paths.get(resource.getURI());
        Path resourceDir = resourcePath.getParent();
        if ( Files.notExists(resourceDir) ) {
            log.info("Directory {} doesn't exist. Creating it now.", resourceDir);
            Files.createDirectories(resourceDir);
        }
    }
}

