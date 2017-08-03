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

// requires bootstrap3-dialog:  https://github.com/nakupanda/bootstrap3-dialog
// helper functions to more easily use this bootstrap3 dialogs

// shows a blue dialog box for information purposes, with an OK button to dismiss
// id should be of the CSS form '#myId'
// msg can be any string
var infoDialogBox = function( id, msg ) {
  $(id).click( function(event) {
      BootstrapDialog.alert({
        message: msg
      });
  });
}

// shows a red dialog box to alert user, with an OK button to dismiss
// id should be of the CSS form '#myId'
// msg can be any string
var alertDialogBox = function( id, msg ) {
  $(id).click( function(event) {
      BootstrapDialog.alert({
        type: BootstrapDialog.TYPE_DANGER,
        message: msg
      });
  });
}

// shows a blue dialog box offering the user to choose between OK and Cancel
// id should be of the CSS form '#myId'
// msg can be any string
// callbackfunction should be of the form
          // function(result) {
          //   if(result) {
          //       alert('Yup.');
          //   }else {
          //       alert('Nope.');
          //   }
          // }
var confirmDialogBox = function( id, msg, callbackFunction ) {
  $(id).click( function(event) {
      BootstrapDialog.confirm({
        title: 'Confirm',
        message: msg,
        draggable: true,
        callback: function(result) {
          callbackFunction( result );
        }
      });
  });
}