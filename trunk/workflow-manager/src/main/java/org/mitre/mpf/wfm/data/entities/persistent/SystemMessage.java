/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data.entities.persistent;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;
import java.time.Instant;

@ApiModel( description="The model for a System Message" )
@Entity
public class SystemMessage {

    /* a unique message ID
     * This can be anything you want, but defaults to the timestamp, and the following are "standard" messages:
     *      eServerPropertiesChanged - server properties have been changed, and the server needs to be restarted (note this automatically removes itself upon server restart)
     */
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @ApiModelProperty( value="Unique ID.", position=0 )
    private long id;

    @ApiModelProperty( value="The message.", position=1 )
    @Column(columnDefinition = "TEXT")
    private String msg;

    @ApiModelProperty( value="The severity of the message", allowableValues = "info, warning, error", position=1 )
    private String severity="info";

    /* the message enum, which is basically a label which uniquely identifies a set of predefined messages.  It is null
     * if the message is not a predefined message
     *  Note that because these predefined messages are unique, they do not show up more than once in the message queue by design.
     *  The intension is that they reflect the state of the server, and not just messages.  Thus when multiple clients
     *  post a predefined message, the last one is removed and the current one added.
     *  These are the enums (predefined messages)
     *    eServerPropertiesChanged - when the server requires a restart, usually set when server properties are changed
     */
    @ApiModelProperty(value = "Enumerated name of predefined message", allowableValues = "eServerPropertiesChanged", position=2)
    private String msgEnum=null;

    /* specifies the type of message
     *  there are 3 types:  // P038: todo: should make these into enums when we have more than one type of message to display
     *     all   (default) Message intended for all users after they log in (this is the only one implemented)
     *     admin Message intended for admin users only
      *    login Message intended for all users before they log in
     */
    @ApiModelProperty(value = "type of message (intended target)", allowableValues = "all,admin,login", position=2)
    private String msgType="all";

    /* specifies how messages are to be removed
     *  there are 3 types:  // P038: todo: should make these into enums when we have more than one type of message to display
     *     manual   (default) Message should be removed when an explicit call to delete the message is received
     *     atServerStartup    Message should automatically be removed when the server starts up (e.g., for eServerNeedsRestart message)
     */
    @ApiModelProperty(value = "strategy for removing this message", allowableValues = "manual,atServerStartup", position=3)
    private String removeStrategy="manual";

    @ApiModelProperty(value = "message timestamp, include only if you want to change the timestamp", hidden = true, access = "hidden", dataType = "java.lang.String")
        /* hidden and access should make this disappear from the documentation (so that it would default to the current date)
         * however, as the springfox developer says here:  http://stackoverflow.com/questions/29206396/spring-mvc-swagger-how-to-hide-model-property-in-swagger-ui
         * we need to upgrade to the latest version of springfox, which, based on past history, is way beyond the scope of this task.
         * I will add this as a subtask to the task for testing this API.  For now, it is noted in the PUT documentation in SystemMessageController.
         */
    @JsonFormat (shape = JsonFormat.Shape.STRING )  // note date is ISO 8601, and can be in GMT or local time as long as the proper timezone is specified;
    private Instant datePosted = Instant.now();

    public SystemMessage() {
    }

    public SystemMessage( String msgEnum ) {
        this();
        this.setMsgEnum( msgEnum );
        if ( msgEnum.equalsIgnoreCase( "eServerPropertiesChanged" ) ) {
            setMsg( "Properties have been modified by an admin. The server must be restarted for the current changes to take effect. Check the Properties Settings page for details on what was changed." );
            setRemoveStrategy( "atServerStartup" );
        }
    }

//    public SystemMessage( String id, String msg ) {
//        set( id, msg );
//    }

//    public void set( String id, String msg ) {
//        setMsgID( id );
//        setMsg(( msg ));
//    }

    public long getId() {
        return id;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        if ( severity==null || severity.length()==0 ) {
            this.severity = "info";
        }
        else {
            this.severity = severity;
        }

    }
    public String getMsgEnum() { return msgEnum; }

    public void setMsgEnum(String msgEnum) {
        if ( msgEnum!=null && msgEnum.equalsIgnoreCase("eServerPropertiesChanged")) {
            this.msgEnum = "eServerPropertiesChanged";
        }
        else {
            this.msgEnum = null;
        }

    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        if ( msgType!=null && ( msgType.equalsIgnoreCase("admin") || msgType.equalsIgnoreCase("login") ) ) {
            this.msgType = msgType.toLowerCase();
        }
        else {
            // default to all
            this.msgType = "all";
        }
    }

    public String getRemoveStrategy() {
        return removeStrategy;
    }

    public void setRemoveStrategy(String removeStrategy) {
        if ( removeStrategy!=null && removeStrategy.equalsIgnoreCase("atServerStartup") ) {
            this.removeStrategy = "atServerStartup";
        }
        else {
            this.removeStrategy = "manual";
        }
    }

    public Instant getDatePosted() {
        return datePosted;
    }

    public void setDatePosted(Instant datePosted) {
        this.datePosted = datePosted;
    }

}
