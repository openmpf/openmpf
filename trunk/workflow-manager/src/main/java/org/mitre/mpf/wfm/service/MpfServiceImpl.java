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

package org.mitre.mpf.wfm.service;

import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.AtmosphereChannel;
import org.mitre.mpf.wfm.data.access.SystemMessageDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateSystemMessageDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.SystemMessage;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MpfServiceImpl implements MpfService {
	private static final Logger log = LoggerFactory.getLogger(MpfServiceImpl.class);

	@Autowired
	@Qualifier(HibernateMarkupResultDaoImpl.REF)
	private MarkupResultDao markupResultDao;

	@Autowired
	@Qualifier(HibernateJobRequestDaoImpl.REF)
	private HibernateDao<JobRequest> jobRequestDao;

	@Autowired
	@Qualifier(JobRequestBoImpl.REF)
	private JobRequestBo jobRequestBo;

	@Autowired
	@Qualifier(HibernateSystemMessageDaoImpl.REF)
	private SystemMessageDao systemMessageDao;

	@Override
	public JsonJobRequest createJob(List<JsonMediaInputObject> media, Map<String,Map> algorithmProperties, Map<String,String> jobProperties, String pipelineName, String externalId, boolean buildOutput, int priority) {
		log.debug("createJob: MediaUris: {}. Pipeline: {}. Build Output: {}. Priority: {}.", (media == null) ? null : media.size(), pipelineName, buildOutput, priority);
		return jobRequestBo.createRequest(externalId, pipelineName, media, algorithmProperties, jobProperties, buildOutput, priority);
	}

	@Override
	public JsonJobRequest createJob(List<JsonMediaInputObject> media, Map<String,Map> algorithmProperties, Map<String,String> jobProperties, String pipelineName, String externalId, boolean buildOutput, int priority, String callbackUrl, String method) {
		log.debug("createJob: MediaUris: {}. Pipeline: {}. Build Output: {}. Priority: {}. Callback: {}. Method: {}.", (media == null) ? null : media.size(), pipelineName, buildOutput, priority, callbackUrl, method);
		return jobRequestBo.createRequest(externalId, pipelineName, media, algorithmProperties, jobProperties, buildOutput, priority, callbackUrl, method);
	}


	@Override
	public long submitJob(JsonJobRequest jobRequest) {
		try {
			return jobRequestBo.run(jobRequest).getId();
		} catch(WfmProcessingException wpe) {
			log.error("Failed to submit job with external id '{}' due to an exception.", jobRequest.getExternalId(), wpe);
			return -1;
		}
	}

	@Override
	public long resubmitJob(long jobId) {
		try {
			return jobRequestBo.resubmit(jobId).getId();
		} catch(WfmProcessingException wpe) {
			log.error("Failed to resubmit job {} due to an exception.", jobId, wpe);
			return -1;
		}
	}
	
	@Override
	public long resubmitJob(long jobId, int newPriority) {
		try {
			return jobRequestBo.resubmit(jobId, newPriority).getId();
		} catch(WfmProcessingException wpe) {
			log.error("Failed to resubmit job {} due to an exception.", jobId, wpe);
			return -1;
		}
	}

	@Override
	public boolean cancel(long jobId) {
		try {
			return jobRequestBo.cancel(jobId);
		} catch(WfmProcessingException wpe) {
			log.error("Failed to cancel Job #{} due to an exception.", jobId, wpe);
			return false;
		}
	}

	@Override
	public MarkupResult getMarkupResult(long id) {
		return markupResultDao.findById(id);
	}

	@Override
	public List<MarkupResult> getMarkupResultsForJob(long jobId) {
		return markupResultDao.findByJobId(jobId);
	}

	@Override
	public List<MarkupResult> getAllMarkupResults() {
		return markupResultDao.findAll();
	}

	@Override
	public JobRequest getJobRequest(long id) {
		return jobRequestDao.findById(id);
	}

	@Override
	public List<JobRequest> getAllJobRequests() {
		return jobRequestDao.findAll();
	}


	/* ***** System Messages ***** */

	private void broadcastSystemMessageChanged( String operation, SystemMessage obj ) {
		HashMap<String,Object> datamap = new HashMap<String,Object>();
		datamap.put( "operation", operation );
		datamap.put( "msgType", (obj!=null) ? obj.getMsgType() : "unknown" );
		datamap.put( "msgID", (obj!=null) ? obj.getId() : "unknown" );
		AtmosphereController.broadcast( AtmosphereChannel.SSPC_SYSTEMMESSAGE, "OnSystemMessagesChanged", datamap );
	}

	@Override
	public List<SystemMessage> getSystemMessagesByType(String filterByType ) {
		if ( filterByType==null || filterByType.equalsIgnoreCase("all") ) {
			return systemMessageDao.findAll();
		}
		else {
			return systemMessageDao.findByType( filterByType );
		}
	}

	@Override
	public List<SystemMessage> getSystemMessagesByRemoveStrategy(String filterbyRemoveStrategy ) {
		if ( filterbyRemoveStrategy==null || filterbyRemoveStrategy.equalsIgnoreCase("all") ) {
			return this.getSystemMessagesByType( null );
		}
		else {
			return systemMessageDao.findByRemoveStrategy( filterbyRemoveStrategy );
		}
	}

	@Override
	public SystemMessage addSystemMessage( SystemMessage obj ) {
		SystemMessage msg = systemMessageDao.add( obj );
		broadcastSystemMessageChanged( "added", obj );
		return msg;
	}

	@Override
	public SystemMessage addStandardSystemMessage( String msgEnum ) {
		SystemMessage obj = systemMessageDao.addStandard( msgEnum );
		broadcastSystemMessageChanged( "added", obj );
		return obj;
	}

	@Override
	public SystemMessage deleteSystemMessage(long msgId ) {
		SystemMessage obj = systemMessageDao.delete( msgId );
		if ( obj != null ) {
			broadcastSystemMessageChanged("deleted", obj);
		}
		return obj;
	}

	@Override
	public SystemMessage deleteStandardSystemMessage(String msgEnum ) {
		SystemMessage obj = systemMessageDao.delete( msgEnum );
		if ( obj != null ) {
			broadcastSystemMessageChanged("deleted", obj);
		}
		return obj;
	}

}

