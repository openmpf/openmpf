/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

"use strict";

angular.module('mpf.wfm.controller.MarkupCtrl', [
    'mpf.wfm.services',
    'ui.bootstrap'
])
.controller('MarkupCtrl', [
'job', '$scope', '$http',
(job, $scope, $http) => {

    Object.assign($scope, {
        job,
        mediaList: [],
        currentPage: 1,
        search: '',
        pageLen: 25,
        hasMorePages: false,
        isLoading() {
            return lastRequestSent != lastResponseReceived;
        },
        pageSizeChanged() {
            $scope.goToPage(1);
        },
        searchChanged() {
            $scope.goToPage(1);
        },
        goToPage(page) {
            $scope.currentPage = page;
            updateMarkup();
        }
    });

    let lastRequestSent = 0;
    let lastResponseReceived = 0;
    const updateMarkup = () => {
        lastRequestSent++;
        const thisRequest = lastRequestSent;
        const params = {
            jobId: job.jobId,
            page: $scope.currentPage,
            pageLen: $scope.pageLen,
            search: $scope.search
        };
        $http.get('markup/get-markup-results-filtered', {params}).then(({data: resp}) => {
            if (thisRequest > lastResponseReceived) {
                lastResponseReceived = thisRequest;
                $scope.recordsTotal = resp.recordsTotal
                $scope.mediaList = resp.media;
                $scope.recordsFiltered = resp.recordsFiltered;
                $scope.hasMorePages = $scope.recordsFiltered > $scope.currentPage * $scope.pageLen;
            }
        });
    }
    updateMarkup();
}
]);