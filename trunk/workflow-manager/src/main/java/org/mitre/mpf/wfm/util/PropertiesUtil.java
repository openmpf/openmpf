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

package org.mitre.mpf.wfm.util;

import org.apache.commons.io.IOUtils;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.*;

@Component(PropertiesUtil.REF)
@Monitored
public class PropertiesUtil {
	private static final Logger log = LoggerFactory.getLogger(PropertiesUtil.class);
	public static final String REF = "propertiesUtil";

	@PostConstruct
	private void init() throws IOException, WfmProcessingException {
		//java 8 to clean up any empty extensions - if there are no custom extensions present, the list has one empty string element
		//and that must be removed
		//I would like to do this in the @Value expression, but I could not find a good solution
		serverMediaTreeCustomExtensions.removeIf(item -> item == null || item.trim().isEmpty());
		log.info("Server media tree custom extensions are '{}'.", serverMediaTreeCustomExtensions.toString());
		createConfigFiles();

		Set<PosixFilePermission> permissions = new HashSet<>();
		permissions.add(PosixFilePermission.OWNER_READ);
		permissions.add(PosixFilePermission.OWNER_WRITE);
		permissions.add(PosixFilePermission.OWNER_EXECUTE);

		Path share = Paths.get(sharePath).toAbsolutePath();
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
		uploadedComponentsDirectory = createOrFail(share, componentUploadDirName, permissions);
		createOrFail(pluginDeploymentPath.toPath(), "",
				EnumSet.of(
						PosixFilePermission.OWNER_READ,
						PosixFilePermission.OWNER_WRITE,
						PosixFilePermission.OWNER_EXECUTE,
						PosixFilePermission.GROUP_READ,
						PosixFilePermission.GROUP_EXECUTE,
						PosixFilePermission.OTHERS_READ,
						PosixFilePermission.OTHERS_EXECUTE
				));

		log.info("All file resources are stored within the shared directory '{}'.", share);
		log.debug("Artifacts Directory = {}", artifactsDirectory);
		log.debug("Markup Directory = {}", markupDirectory);
		log.debug("Output Objects Directory = {}", outputObjectsDirectory);
		log.debug("Remote Media Cache Directory = {}", remoteMediaCacheDirectory);
		log.debug("Uploaded Components Directory = {}", uploadedComponentsDirectory);
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

	//
	// JMX Configuration
	//

	@Value("${jmx.amq.broker.enabled}")
	private boolean amqBrokerEnabled;
	public boolean isAmqBrokerEnabled() { return amqBrokerEnabled; }

	@Value("${jmx.amq.broker.uri}")
	private String amqBrokerUri;
	public String getAmqBrokerUri() { return amqBrokerUri; }

	@Value("${jmx.amq.broker.admin.username}")
	private String amqBrokerAdminUsername;
	public String getAmqBrokerAdminUsername() { return amqBrokerAdminUsername; }

	@Value("${jmx.amq.broker.admin.password}")
	private String amqBrokerAdminPassword;
	public String getAmqBrokerAdminPassword() { return amqBrokerAdminPassword; }

	@Value("#{'${jmx.amq.broker.whiteList}'.split(',')}")
	private String[] amqBrokerPurgeWhiteList;
	public Set<String> getAmqBrokerPurgeWhiteList() {
		return new HashSet<>(Arrays.asList(amqBrokerPurgeWhiteList));
	}

	//
	// Main Configuration
	//

	@Value("${output.site.name}")
	private String siteId;
	public String getSiteId() { return siteId; }

	@Value("${mpf.output.objects.enabled}")
	private boolean outputObjectsEnabled;
	public boolean isOutputObjectsEnabled() { return outputObjectsEnabled; }

	@Value("${mpf.output.objects.queue.enabled}")
	private boolean outputQueueEnabled;
	public boolean isOutputQueueEnabled() { return outputQueueEnabled; }

	@Value("${mpf.output.objects.queue.name}")
	private String outputQueueName;
	public String getOutputQueueName() { return outputQueueName; }

	@Value("${mpf.share.path}")
	private String sharePath;

	private File artifactsDirectory;
	public File getArtifactsDirectory() { return artifactsDirectory; }
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
	/** Create the output objects directory for batch jobs
	 * @param jobId unique id that has been assigned to the batch job
	 * @return directory that was created under the output objects directory for storage of files from this batch job
	 * @throws IOException
	 */
	public File createDetectionOutputObjectFile(long jobId) throws IOException {
		return createOutputObjectsFile(jobId, "detection");
	}
	/** Create the output objects directory for a streaming job
	 * @param jobId unique id that has been assigned to the streaming job
	 * @return directory that was created under the output objects directory for storage of files from this streaming job
	 * @throws IOException
	 */
	public File createStreamingOutputObjectsDirectory(long jobId) throws IOException {
		return createOutputObjectsDirectory(jobId, "streaming-output");
	}
	/** Create the output object file in the specified streaming job output objects directory
	 * @param jobId unique id that has been assigned to the streaming job
	 * @param parentDir this streaming jobs output objects directory
	 * @return output object File that was created under the specified output objects directory
	 * @throws IOException
	 */
	public File createStreamingOutputObjectsFile(long jobId, File parentDir) throws IOException {
		return createOutputObjectsFile(jobId, parentDir, "detection");
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

	/** Create the directory path to be used for storing output objects for a job, return the path to that directory as a File
	 * This method is typically used for streaming jobs
	 * @param jobId unique id that has been assigned to the job
	 * @param outputObjectDirectoryName name of the sub-directory to be used for storing the output objects from this streaming job
	 * @return File (directory) to be used for storing an output object for this job
	 * @throws IOException
	 */
	private File createOutputObjectsDirectory(long jobId, String outputObjectDirectoryName) throws IOException {
		String fileName = String.format("%s/%d", TextUtils.trimToEmpty(outputObjectDirectoryName), jobId);
		Path path = Paths.get(outputObjectsDirectory.toURI()).resolve(fileName).normalize().toAbsolutePath();
		Files.createDirectories(path);
		return path.toFile();
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
	public Path createMarkupPath(long jobId, long mediaId, String extension) throws IOException {
		Path path = Paths.get(markupDirectory.toURI()).resolve(String.format("%d/%d/%s%s", jobId, mediaId, UUID.randomUUID(), TextUtils.trimToEmpty(extension))).normalize().toAbsolutePath();
		Files.createDirectories(path.getParent());
		return Files.createFile(path);
	}

	//
	// Detection Configuration
	//

	@Value("${detection.artifact.extraction.policy}")
	private ArtifactExtractionPolicy artifactExtractionPolicy;
	public ArtifactExtractionPolicy getArtifactExtractionPolicy() { return artifactExtractionPolicy; }

	@Value("${detection.sampling.interval}")
	private int samplingInterval;
	public int getSamplingInterval() { return samplingInterval; }

	@Value("${detection.confidence.threshold}")
	private double confidenceThreshold;
	public double getConfidenceThreshold() { return confidenceThreshold; }

	@Value("${detection.segment.minimum.gap}")
	private int minAllowableSegmentGap;
	public int getMinAllowableSegmentGap() { return minAllowableSegmentGap; }

	@Value("${detection.segment.target.length}")
	private int targetSegmentLength;
	public int getTargetSegmentLength() { return targetSegmentLength; }

	@Value("${detection.segment.minimum.length}")
	private int minSegmentLength;
	public int getMinSegmentLength() { return minSegmentLength; }

	@Value("${detection.track.merging.enabled}")
	private boolean trackMerging;
	public boolean isTrackMerging() { return trackMerging; }

	@Value("${detection.track.min.gap}")
	private int minAllowableTrackGap;
	public int getMinAllowableTrackGap() { return minAllowableTrackGap; }

	@Value("${detection.track.minimum.length}")
	private int minTrackLength;
	public int getMinTrackLength() { return minTrackLength; }

	@Value("${detection.track.overlap.threshold}")
	private double trackOverlapThreshold;
	public double getTrackOverlapThreshold() { return trackOverlapThreshold; }

	//
	// JMS Configuration
	//

	@Value("${jms.priority}")
	private int jmsPriority;
	public int getJmsPriority() { return jmsPriority; }

	//
	// Pipeline Configuration
	//

	@Value("${data.algorithms.file}")
	private FileSystemResource algorithmsData;

	@Value("${data.algorithms.template}")
	private Resource algorithmsTemplate;

	public WritableResource getAlgorithmDefinitions() {
		return getDataResource(algorithmsData, algorithmsTemplate);
	}

	@Value("${data.actions.file}")
	private FileSystemResource actionsData;

	@Value("${data.actions.template}")
	private Resource actionsTemplate;

	public WritableResource getActionDefinitions() {
		return getDataResource(actionsData, actionsTemplate);
	}

	@Value("${data.tasks.file}")
	private FileSystemResource tasksData;

	@Value("${data.tasks.template}")
	private Resource tasksTemplate;

	public WritableResource getTaskDefinitions() {
		return getDataResource(tasksData, tasksTemplate);
	}

	@Value("${data.pipelines.file}")
	private FileSystemResource pipelinesData;

	@Value("${data.pipelines.template}")
	private Resource pipelinesTemplate;

	public WritableResource getPipelineDefinitions() {
		return getDataResource(pipelinesData, pipelinesTemplate);
	}

	@Value("${data.nodemanagerpalette.file}")
	private FileSystemResource nodeManagerPaletteData;

	@Value("${data.nodemanagerpalette.template}")
	private Resource nodeManagerPaletteTemplate;

	public WritableResource getNodeManagerPalette() {
		return getDataResource(nodeManagerPaletteData, nodeManagerPaletteTemplate);
	}

	@Value("${data.nodemanagerconfig.file}")
	private FileSystemResource nodeManagerConfigData;

	@Value("${data.nodemanagerconfig.template}")
	private Resource nodeManagerConfigTemplate;
	public WritableResource getNodeManagerConfigResource() {
		return getDataResource(nodeManagerConfigData, nodeManagerConfigTemplate);
	}

	//
	// Component upload and registration properties
	//

	@Value("${data.component.info.file}")
	private FileSystemResource componentInfo;

	@Value("${data.component.info.template}")
	private Resource componentInfoTemplate;

	public WritableResource getComponentInfoFile() {
		return getDataResource(componentInfo, componentInfoTemplate);
	}

	private File uploadedComponentsDirectory;
	public File getUploadedComponentsDirectory() { return uploadedComponentsDirectory; }

	//should not need these outside of this file
	@Value("${component.upload.dir.name}")
	private String componentUploadDirName;

	@Value("${mpf.component.dependency.finder.script}")
	private File componentDependencyFinderScript;
	public File getComponentDependencyFinderScript() {
		return componentDependencyFinderScript;
	}

	@Value("${mpf.plugins.path}")
	private File pluginDeploymentPath;
	public File getPluginDeploymentPath() {
		return pluginDeploymentPath;
	}

	// Defaults to zero if property not set.
	@Value("${startup.num.services.per.component:0}")
	private int numStartUpServices;
	public int getNumStartUpServices() {
		return numStartUpServices;
	}

	@Value("${startup.auto.registration.skip.spring}")
	private boolean startupAutoRegistrationSkipped;
	public boolean isStartupAutoRegistrationSkipped() {
		return startupAutoRegistrationSkipped;
	}

	public String getThisMpfNodeHostName() {
		return System.getenv("THIS_MPF_NODE");
	}

	//
	// Web Settings
	//

	// directory under which log directory is located: <log.parent.dir>/<hostname>/log
	@Value("${log.parent.dir}")
	private String logParentDir;
	public String getLogParentDir() {
		return logParentDir;
	}

	@Value("#{'${web.active.profiles}'.split(',')}")
	private List<String> webActiveProfiles;
	public List<String> getWebActiveProfiles() {
        return webActiveProfiles;
    }

	@Value("${web.session.timeout}")
	private int webSessionTimeout;
	public int getWebSessionTimeout() { return webSessionTimeout; }

	@Value("${web.server.media.tree.base}")
	private String serverMediaTreeRoot;
	public String getServerMediaTreeRoot() { return serverMediaTreeRoot; }

	@Value("#{'${web.server.media.tree.custom.extensions}'.split(',')}")
	private List<String> serverMediaTreeCustomExtensions; // modifications are made in @PostConstruct
	public List<String> getServerMediaTreeCustomExtensions() {
		return serverMediaTreeCustomExtensions;
	}

	@Value("${web.max.file.upload.cnt}")
	private int webMaxFileUploadCnt;
	public int getWebMaxFileUploadCnt() { return webMaxFileUploadCnt; }

	//
	// Version information
	//
	@Value("${mpf.version.semantic}")
	private String semanticVersion;
	public String getSemanticVersion() {
		return semanticVersion;
	}

	@Value("${mpf.version.git.hash}")
	private String gitHash;
	public String getGitHash() {
		return gitHash;
	}

	@Value("${mpf.version.git.branch}")
	private String gitBranch;
	public String getGitBranch() {
		return gitBranch;
	}

	@Value("${mpf.version.jenkins.buildnum}")
	private String buildNum;
	public String getBuildNum() {
		return buildNum;
	}

	@Value("${mpf.version.json.output.object.schema}")
	private String outputObjectVersion;
	public String getOutputObjectVersion() {
		return outputObjectVersion;
	}

	@Value("${config.mediaTypes.file}")
	private FileSystemResource mediaTypesFile;

	@Value("${config.mediaTypes.template}")
	private Resource mediaTypesTemplate;

	@Value("${config.custom.properties.file}")
	private FileSystemResource customPropertiesFile;
	public FileSystemResource getCustomPropertiesFile() {
		return customPropertiesFile;
	}

	private void createConfigFiles() throws IOException {
		if (!mediaTypesFile.exists()) {
			copyResource(mediaTypesFile, mediaTypesTemplate);
		}

		if (!customPropertiesFile.exists()) {
			createParentDir(customPropertiesFile);
			customPropertiesFile.getFile().createNewFile();
		}
	}

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

	private static void createParentDir(Resource resource) throws IOException {
		Path resourcePath = Paths.get(resource.getURI());
		Path resourceDir = resourcePath.getParent();
		if ( Files.notExists(resourceDir) ) {
			log.info("Directory {} doesn't exist. Creating it now.", resourceDir);
			Files.createDirectories(resourceDir);
		}
	}

}

