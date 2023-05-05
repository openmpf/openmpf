/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.AtmosphereChannel;
import org.mitre.mpf.wfm.data.access.SystemMessageDao;
import org.mitre.mpf.wfm.data.entities.persistent.SystemMessage;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemMessageService {

    private final SystemMessageDao _systemMessageDao;

    @Inject
    SystemMessageService(SystemMessageDao systemMessageDao) {
        _systemMessageDao = systemMessageDao;
    }


    public SystemMessage addStandardSystemMessage(String msgID) {
        SystemMessage obj = _systemMessageDao.addStandard(msgID);
        broadcastSystemMessageChanged("added", obj);
        return obj;
    }


    public SystemMessage addSystemMessage(SystemMessage obj) {
        SystemMessage msg = _systemMessageDao.add(obj);
        broadcastSystemMessageChanged("added", obj);
        return msg;
    }


    public List<SystemMessage> getSystemMessagesByType(String filterByType) {
        if (filterByType == null || filterByType.equalsIgnoreCase("all") ) {
            return _systemMessageDao.findAll();
        }
        else {
            return _systemMessageDao.findByType(filterByType);
        }
    }


    public List<SystemMessage> getSystemMessagesByRemoveStrategy(String filterbyRemoveStrategy) {
        if (filterbyRemoveStrategy == null || filterbyRemoveStrategy.equalsIgnoreCase("all")) {
            return getSystemMessagesByType( null );
        }
        else {
            return _systemMessageDao.findByRemoveStrategy( filterbyRemoveStrategy );
        }
    }



    public SystemMessage deleteStandardSystemMessage(String msgEnum ) {
        SystemMessage obj = _systemMessageDao.delete( msgEnum );
        if (obj != null) {
            broadcastSystemMessageChanged("deleted", obj);
        }
        return obj;
    }



    public SystemMessage deleteSystemMessage(long msgId ) {
        SystemMessage obj = _systemMessageDao.delete(msgId);
        if (obj != null) {
            broadcastSystemMessageChanged("deleted", obj);
        }
        return obj;
    }


    private static void broadcastSystemMessageChanged(String operation, SystemMessage obj) {
        Map<String, Object> datamap = new HashMap<String, Object>();
        datamap.put("operation", operation);
        datamap.put("msgType", (obj!=null) ? obj.getMsgType() : "unknown");
        datamap.put("msgID", (obj!=null) ? obj.getId() : "unknown");
        AtmosphereController.broadcast(AtmosphereChannel.SSPC_SYSTEMMESSAGE, "OnSystemMessagesChanged", datamap);
    }
}
