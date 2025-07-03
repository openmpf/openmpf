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

package org.mitre.mpf.wfm.util;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.EnvVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.io.MoreFiles;

@Component("wfmPropertiesUtil")
@Monitored
public class PropertiesUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesUtil.class);

    private final ApplicationContext _appContext;

    private final MpfPropertiesConfigurationBuilder _mpfPropertiesConfigBuilder;

    private final Environment _springEnvironment;

    private final FileSystemResource _mediaTypesFile;

    private final FileSystemResource _userFile;

    private ImmutableConfiguration _mpfPropertiesConfig;

    // The set of core nodes will not change while the WFM is running.
    private final ImmutableSet<String> _coreMpfNodes;

    @Inject
    PropertiesUtil(
            ApplicationContext appContext,
            MpfPropertiesConfigurationBuilder mpfPropertiesConfigBuilder,
            Environment springEnvironment,
            @Named("mediaTypesFile") FileSystemResource mediaTypesFile,
            @Named("userFile") FileSystemResource userFile
    ) throws WfmProcessingException, IOException {
        _appContext = appContext;
        _mpfPropertiesConfigBuilder = mpfPropertiesConfigBuilder;
        _springEnvironment = springEnvironment;
        _mediaTypesFile = mediaTypesFile;
        _userFile = userFile;
        _coreMpfNodes = parseCoreMpfNodes();

        _mpfPropertiesConfig = mpfPropertiesConfigBuilder.getCompleteConfiguration();

        if (!mediaTypesFile.exists()) {
            copyResource(mediaTypesFile, getMediaTypesTemplate());
        }

        if (!userFile.exists()) {
            copyResource(userFile, getUserTemplate());
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
        remoteMediaDirectory = createOrFail(share, "remote-media", permissions);
        temporaryMediaDirectory = createOrClear(share, "tmp", permissions);
        derivativeMediaDirectory = createOrFail(share, "derivative-media", permissions);
        mediaSelectorsOutputDir = createOrFail(
                share, "media-selectors-output", permissions).toPath();
        uploadedComponentsDirectory = createOrFail(share, getComponentUploadDirName(), permissions);
        createOrFail(getPluginDeploymentPath(), "",
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

        LOG.info("All file resources are stored within the shared directory '{}'.", share);
        LOG.debug("Artifacts Directory = {}", artifactsDirectory);
        LOG.debug("Markup Directory = {}", markupDirectory);
        LOG.debug("Output Objects Directory = {}", outputObjectsDirectory);
        LOG.debug("Remote Media Directory = {}", remoteMediaDirectory);
        LOG.debug("Temporary Media Directory = {}", temporaryMediaDirectory);
        LOG.debug("Derivative Media Directory = {}", derivativeMediaDirectory);
        LOG.debug("Uploaded Components Directory = {}", uploadedComponentsDirectory);
    }


    private ImmutableSet<String> parseCoreMpfNodes() {
        String coreMpfNodesStr = System.getenv(EnvVar.CORE_MPF_NODES);

        if (coreMpfNodesStr == null || coreMpfNodesStr.isBlank()) {
            return ImmutableSet.of();
        }
        else {
            return Arrays.stream(coreMpfNodesStr.split(","))
                    .map(String::trim)
                    .filter(node -> !node.isEmpty())
                    .collect(ImmutableSet.toImmutableSet());
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

    private static File createOrClear(Path parent, String subdirectory, Set<PosixFilePermission> permissions)
            throws IOException, WfmProcessingException {
        Path child = parent.resolve(subdirectory);
        if ( Files.exists(child) ) {
            MoreFiles.deleteDirectoryContents(child);
            return child.toAbsolutePath().toFile();
        } else {
            return createOrFail(parent, subdirectory, permissions);
        }
    }

    public String lookup(String propertyName) {
        return _mpfPropertiesConfig.getString(propertyName);
    }

    public void setAndSaveCustomProperties(List<PropertyModel> propertyModels) {
        _mpfPropertiesConfig = _mpfPropertiesConfigBuilder.setAndSaveCustomProperties(propertyModels);
    }

    /**
     * Returns a updated list of property models. Each element contains current value,
     * as well as a flag indicating whether or not a WFM restart is required to apply the change.
     * @return Updated list of property models.
     */
    public List<PropertyModel> getCustomProperties() {
        return _mpfPropertiesConfigBuilder.getCustomProperties();
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

    public SystemPropertiesSnapshot createSystemPropertiesSnapshot() {
        Iterator<String> snapshotProps = Iterators.filter(_mpfPropertiesConfig.getKeys(),
                                                          MpfPropertiesConfigurationBuilder::propertyRequiresSnapshot);
        return new SystemPropertiesSnapshot(Maps.toMap(snapshotProps, _mpfPropertiesConfig::getString));
    }


    //
    // Main configuration
    //

    public String getSiteId() {
        return _mpfPropertiesConfig.getString("output.site.name");
    }

    public String getHostName() {
        return Objects.requireNonNullElseGet(
                System.getenv("NODE_HOSTNAME"),
                () -> System.getenv("HOSTNAME"));
    }

    public long getJobIdFromExportedId(String exportedId) {
        String[] tokens = exportedId.split("-");
        try {
            return Long.parseLong(tokens[tokens.length - 1]);
        }
        catch (NumberFormatException e) {
            throw new InvalidJobIdException(
                    "Failed to parse job id of '" + exportedId +
                            "'. Expected a job id like <hostname>-<integer>.", e);
        }
    }

    public String getExportedJobId(long jobId) {
        return getHostName() + '-' + jobId;
    }

    public boolean isStreamingOutputObjectsToDiskEnabled() {
        return _mpfPropertiesConfig.getBoolean("mpf.streaming.output.objects.to.disk.enabled");
    }

    public boolean isOutputObjectsArtifactsAndExemplarsOnly() {
        return _mpfPropertiesConfig.getBoolean("mpf.output.objects.artifacts.and.exemplars.only");
    }

    public Set<String> getCensoredOutputProperties() {
        return new HashSet<>(_mpfPropertiesConfig.getList(
                String.class, "mpf.output.objects.censored.properties"));
    }

    public String getSharePath() {
        return _mpfPropertiesConfig.getString("mpf.share.path");
    }

    private File artifactsDirectory;
    public File getJobArtifactsDirectory(long jobId) {
        return new File(artifactsDirectory, String.valueOf(jobId));
    }

    public File createArtifactDirectory(long jobId, long mediaId, int taskIndex,
                                        int actionIndex) throws IOException {
        Path path = Paths.get(artifactsDirectory.toURI()).resolve(String.format("%d/%d/%d/%d/", jobId, mediaId, taskIndex, actionIndex)).normalize().toAbsolutePath();
        Files.createDirectories(path);
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
    public Path createDetectionOutputObjectFile(long jobId) throws IOException {
        return createOutputObjectsFile(jobId, "detection");
    }

    /** Create the output objects directory for a job
     * Note: this method is typically used by streaming jobs.
     * The WFM will need to create the directory before it is populated with files.
     * @param jobId unique id that has been assigned to the job
     * @return directory that was created under the output objects directory for storage of files from this job
     * @throws IOException
     */
    public File createOutputObjectsDirectory(long jobId) {
        try {
            String fileName = String.format("%d", jobId);
            Path path = Paths.get(outputObjectsDirectory.toURI()).resolve(fileName).normalize().toAbsolutePath();
            Files.createDirectories(path);
            return path.toFile();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Create the output object file in the specified streaming job output objects directory
     * @param time the time associated with the job output
     * @param parentDir this streaming job's output objects directory
     * @return output object File that was created under the specified output objects directory
     * @throws IOException
     */
    public File createStreamingOutputObjectsFile(Instant time, File parentDir) throws IOException {
        String fileName = String.format("summary-report %s.json", TimeUtils.toIsoString(time));
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
    public Path createOutputObjectsFile(long jobId, String outputObjectType) throws IOException {
        return createOutputObjectsFile(jobId,outputObjectsDirectory,outputObjectType);
    }

    /** Create the File to be used for storing output objects from a job, plus create the directory path to that File
     * @param jobId unique id that has been assigned to the job
     * @param parentDir parent directory for the file to be created
     * @param outputObjectType pre-defined type of output object for the job
     * @return File to be used for storing an output object for this job
     * @throws IOException
     */
    private static Path createOutputObjectsFile(long jobId, File parentDir, String outputObjectType) throws IOException {
        String fileName = String.format("%d/%s.json", jobId, TextUtils.trimToEmpty(outputObjectType));
        Path path = Paths.get(parentDir.toURI()).resolve(fileName).normalize().toAbsolutePath();
        Files.createDirectories(path.getParent());
        return path;
    }

    private Path mediaSelectorsOutputDir;
    public Path getMediaSelectorsOutputDir() {
        return mediaSelectorsOutputDir;
    }

    private File remoteMediaDirectory;
    public File getRemoteMediaDirectory() { return remoteMediaDirectory; }

    private File temporaryMediaDirectory;
    public File getTemporaryMediaDirectory() { return temporaryMediaDirectory; }

    private File derivativeMediaDirectory;
    public File getJobDerivativeMediaDirectory(long jobId) {
        return new File(derivativeMediaDirectory, String.valueOf(jobId));
    }

    public Path createDerivativeMediaPath(long jobId, Media media) {
        var extension = FilenameUtils.getExtension(media.getLocalPath().getFileName().toString());
        try {
            Path path = Paths.get(getJobDerivativeMediaDirectory(jobId).toURI())
                    .resolve(String.format("%d/%s%s", media.getParentId(), UUID.randomUUID(), "." + extension))
                    .normalize()
                    .toAbsolutePath();
            Files.createDirectories(path.getParent());
            return path;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private File markupDirectory;
    public File getJobMarkupDirectory(long jobId) {
        return new File(markupDirectory, String.valueOf(jobId));
    }

    //
    // Detection configuration
    //

    public ArtifactExtractionPolicy getArtifactExtractionPolicy() {
        return _mpfPropertiesConfig.get(ArtifactExtractionPolicy.class, "detection.artifact.extraction.policy");
    }

    public Set<String> getArtifactExtractionNonVisualTypesList() {
        return new HashSet<>(_mpfPropertiesConfig.getList(String.class, "detection.artifact.extraction.nonvisual.types"));
    }

    public Set<String> getIllFormedDetectionRemovalExemptionList() {
        return new HashSet<>(_mpfPropertiesConfig.getList(String.class, "detection.illformed.detection.removal.exempt.types"));
    }

    public Set<String> getTrackMergingExemptionList() {
        return new HashSet<>(_mpfPropertiesConfig.getList(String.class, "detection.video.track.merging.exempt.types"));
    }

    public int getArtifactParallelUploadCount() {
        return _mpfPropertiesConfig.getInt("detection.artifact.extraction.parallel.upload.count");
    }

    public int getDerivativeMediaParallelUploadCount() {
        return _mpfPropertiesConfig.getInt("detection.derivative.media.parallel.upload.count");
    }

    public int getSamplingInterval() {
        return _mpfPropertiesConfig.getInt("detection.sampling.interval");
    }

    //
    // JMS configuration
    //

    public int getJmsPriority() {
        return _mpfPropertiesConfig.getInt("jms.priority");
    }

    //
    // Pipeline configuration
    //

    private FileSystemResource getAlgorithmsData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.algorithms.file"));
    }

    private Resource getAlgorithmsTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.algorithms.template"));
    }

    public WritableResource getAlgorithmDefinitions() {
        return getDataResource(getAlgorithmsData(), getAlgorithmsTemplate());
    }

    private FileSystemResource getActionsData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.actions.file"));
    }

    private Resource getActionsTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.actions.template"));
    }

    public WritableResource getActionDefinitions() {
        return getDataResource(getActionsData(), getActionsTemplate());
    }

    private FileSystemResource getTasksData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.tasks.file"));
    }

    private Resource getTasksTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.tasks.template"));
    }

    public WritableResource getTaskDefinitions() {
        return getDataResource(getTasksData(), getTasksTemplate());
    }

    private FileSystemResource getPipelinesData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.pipelines.file"));
    }

    private Resource getPipelinesTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.pipelines.template"));
    }

    public WritableResource getPipelineDefinitions() {
        return getDataResource(getPipelinesData(), getPipelinesTemplate());
    }


    private FileSystemResource getTiesDbCheckIgnorablePropertiesData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString(
                "data.ties.db.check.ignorable.properties.file"));
    }

    private Resource getTiesDbCheckIgnorablePropertiesTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString(
                "data.ties.db.check.ignorable.properties.template"));
    }

    public Resource getTiesDbCheckIgnorablePropertiesResource() {
        return getDataResource(
                getTiesDbCheckIgnorablePropertiesData(),
                getTiesDbCheckIgnorablePropertiesTemplate());
    }


    private FileSystemResource getNodeManagerPaletteData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.nodemanagerpalette.file"));
    }

    private Resource getNodeManagerPaletteTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.nodemanagerpalette.template"));
    }

    public WritableResource getNodeManagerPalette() {
        return getDataResource(getNodeManagerPaletteData(), getNodeManagerPaletteTemplate());
    }

    private FileSystemResource getNodeManagerConfigData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.nodemanagerconfig.file"));
    }

    private Resource getNodeManagerConfigTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.nodemanagerconfig.template"));
    }

    public WritableResource getNodeManagerConfigResource() {
        return getDataResource(getNodeManagerConfigData(), getNodeManagerConfigTemplate());
    }


    private FileSystemResource getStreamingServicesData() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.streamingprocesses.file"));
    }

    private Resource getStreamingServicesTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.streamingprocesses.template"));
    }

    public WritableResource getStreamingServices() {
        return getDataResource(getStreamingServicesData(), getStreamingServicesTemplate());
    }


    //
    // Component upload and registration properties
    //

    private FileSystemResource getComponentInfo() {
        return new FileSystemResource(_mpfPropertiesConfig.getString("data.component.info.file"));
    }

    private Resource getComponentInfoTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("data.component.info.template"));
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
        return _mpfPropertiesConfig.getString("component.upload.dir.name");
    }

    public Path getPluginDeploymentPath() {
        return Paths.get(_mpfPropertiesConfig.getString("mpf.plugins.path"));
    }

    public boolean isStartupAutoRegistrationSkipped() {
        String key = "startup.auto.registration.skip.spring";
        try {
            return _mpfPropertiesConfig.getBoolean(key, false);
        } catch (ConversionException e) {
            if (_mpfPropertiesConfig.getString(key).startsWith("${")) {
                LOG.warn("Unable to determine value for \"" + key + "\". It may not have been set via Maven. Using default value of \"false\".");
                return false;
            }
            throw e;
        }
    }

    public Set<String> getCoreMpfNodes() {
        return _coreMpfNodes;
    }

    //
    // Web settings
    //

    // directory under which log directory is located: <log.parent.dir>/<hostname>/log
    public String getLogParentDir() {
        return _mpfPropertiesConfig.getString("log.parent.dir");
    }

    public String getServerMediaTreeRoot() {
        return _mpfPropertiesConfig.getString("web.server.media.tree.base");
    }

    public int getWebMaxFileUploadCnt() {
        return _mpfPropertiesConfig.getInt("web.max.file.upload.cnt");
    }

    public boolean isBroadcastJobStatusEnabled() {
        return _mpfPropertiesConfig.getBoolean("web.broadcast.job.status.enabled");
    }

    //
    // Version information
    //

    public String getSemanticVersion() {
        return _mpfPropertiesConfig.getString("mpf.version.semantic");
    }

    public String getOutputObjectVersion() {
        return _mpfPropertiesConfig.getString("mpf.version.json.output.object.schema");
    }


    public FileSystemResource getMediaTypesFile() {
        return _mediaTypesFile;
    }

    private Resource getMediaTypesTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("config.mediaTypes.template"));
    }


    public FileSystemResource getUserFile() {
        return _userFile;
    }

    private Resource getUserTemplate() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("config.user.template"));
    }


    public String getAmqUri() {
        return _mpfPropertiesConfig.getString("amq.broker.uri");
    }

    public int getAmqConcurrentConsumers() {
        return _mpfPropertiesConfig.getInt("amq.concurrent.consumers", 60);
    }

    public String getAmqOpenWireBindAddress() {
        return _mpfPropertiesConfig.getString("amq.open.wire.bind.address");
    }

    public String getAmqAmqpBindAddress() {
        return _mpfPropertiesConfig.getString("amq.amqp.bind.address");
    }

    //
    // Streaming job properties
    //

    /**
     * Get the health report callback rate, in milliseconds
     * @return health report callback rate, in milliseconds
     */
    public long getStreamingJobHealthReportCallbackRate() {
        return _mpfPropertiesConfig.getLong("streaming.healthReport.callbackRate");
    }

    /**
     * Get the streaming job stall alert threshold, in milliseconds
     * @return streaming job stall alert threshold, in milliseconds
     */
    public long getStreamingJobStallAlertThreshold() {
        return _mpfPropertiesConfig.getLong("streaming.stallAlert.detectionThreshold");
    }

    //
    // Ansible configuration
    //

    public String getAnsibleCompDeployPath() {
        return _mpfPropertiesConfig.getString("mpf.ansible.compdeploy.path");
    }

    public String getAnsibleCompRemovePath() {
        return _mpfPropertiesConfig.getString("mpf.ansible.compremove.path");
    }

    public boolean isAnsibleLocalOnly() {
        return _mpfPropertiesConfig.getBoolean("mpf.ansible.local-only", false);
    }

    //
    // Remote media settings
    //

    public int getRemoteMediaDownloadRetries() {
        return _mpfPropertiesConfig.getInt("remote.media.download.retries");
    }

    public int getRemoteMediaDownloadSleep() {
        return _mpfPropertiesConfig.getInt("remote.media.download.sleep");
    }

    //
    // Node management settings
    //

    public boolean isNodeAutoConfigEnabled() {
        return _mpfPropertiesConfig.getBoolean("node.auto.config.enabled");
    }

    public boolean isNodeAutoUnconfigEnabled() {
        return _mpfPropertiesConfig.getBoolean("node.auto.unconfig.enabled");
    }

    public int getNodeAutoConfigNumServices() {
        return _mpfPropertiesConfig.getInt("node.auto.config.num.services.per.component");
    }

    // Helper methods

    private static WritableResource getDataResource(WritableResource dataResource, InputStreamSource templateResource) {
        if (dataResource.exists()) {
            return dataResource;
        }

        try {
            LOG.info("{} doesn't exist. Copying from {}", dataResource, templateResource);
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
            LOG.info("Directory {} doesn't exist. Creating it now.", resourceDir);
            Files.createDirectories(resourceDir);
        }
    }

    public int getNginxStorageUploadThreadCount() {
        return _mpfPropertiesConfig.getInt("http.object.storage.nginx.upload.thread.count");
    }

    public int getNginxStorageUploadSegmentSize() {
        return _mpfPropertiesConfig.getInt("http.object.storage.nginx.upload.segment.size");
    }

    public int getHttpStorageUploadRetryCount() {
        return _mpfPropertiesConfig.getInt("http.object.storage.upload.retry.count");
    }

    public Resource getWorkflowPropertiesFile() {
        return _appContext.getResource(_mpfPropertiesConfig.getString("workflow.properties.file"));
    }

    public boolean dockerProfileEnabled() {
        return _springEnvironment.acceptsProfiles(Profiles.of("docker"));
    }

    public int getHttpCallbackTimeoutMs() {
        return _mpfPropertiesConfig.getInt("http.callback.timeout.ms");
    }

    public int getHttpCallbackRetryCount() {
        return _mpfPropertiesConfig.getInt("http.callback.retries");
    }

    public int getHttpCallbackConcurrentConnections() {
        return _mpfPropertiesConfig.getInt("http.callback.concurrent.connections");
    }

    public int getHttpCallbackConcurrentConnectionsPerRoute() {
        return _mpfPropertiesConfig.getInt("http.callback.concurrent.connections.per.route");
    }

    public int getHttpCallbackSocketTimeout() {
        return _mpfPropertiesConfig.getInt("http.callback.socket.timeout.ms");
    }

    public int getWarningFrameCountDiff() {
        return _mpfPropertiesConfig.getInt("warn.frame.count.diff");
    }

    public int getProtobufSizeLimit() {
        return _mpfPropertiesConfig.getInt("mpf.protobuf.max.size");
    }

    public int getS3ClientCacheCount() {
        return _mpfPropertiesConfig.getInt("static.s3.client.cache.count", 40);
    }

    public String getOutputChangedCounter() {
        return _mpfPropertiesConfig.getString("output.changed.counter");
    }
}
