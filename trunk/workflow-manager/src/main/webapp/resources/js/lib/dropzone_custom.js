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

//https://github.com/enyo/dropzone/issues/690
Dropzone.prototype.updateTotalUploadProgress = function() {
    var activeFiles, file, totalBytes, totalBytesSent, totalUploadProgress, _i, _len, _ref;
    totalBytesSent = 0;
    totalBytes = 0;
    activeFiles = this.getActiveFiles();
    if (activeFiles.length) {
        //Bugfix
        var sentFiles = this.getFilesWithStatus(Dropzone.SUCCESS);
        for (var _i_sent = 0; _i_sent < sentFiles.length; _i_sent++) {
            totalBytesSent += sentFiles[_i_sent].upload.bytesSent;
            totalBytes += sentFiles[_i_sent].upload.total;
        }
        //Bugfix end
        _ref = this.getActiveFiles();
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
            file = _ref[_i];
            totalBytesSent += file.upload.bytesSent;
            totalBytes += file.upload.total;
        }
        totalUploadProgress = 100 * totalBytesSent / totalBytes;
    } else {
        totalUploadProgress = 100;
    }
    return this.emit("totaluploadprogress", totalUploadProgress, totalBytes, totalBytesSent);
};
