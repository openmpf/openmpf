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

"use strict";

angular.module('mpf.wfm.controller.MarkupCtrl', [
    'mpf.wfm.services',
    'ui.bootstrap'
])
.controller('MarkupCtrl', [
'job', '$scope', '$http', '$sce',
(job, $scope, $http, $sce) => {

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
        },
        sourceIsDataUri(media) {
            return media.sourceUri.startsWith('data:');
        },
        getVideoUrl(media) {
            return $sce.trustAsResourceUrl(media.sourceDownloadUrl);
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
            if (thisRequest < lastResponseReceived) {
                return;
            }
            lastResponseReceived = thisRequest;
            $scope.recordsTotal = resp.recordsTotal
            $scope.recordsFiltered = resp.recordsFiltered;
            $scope.hasMorePages = $scope.recordsFiltered > $scope.currentPage * $scope.pageLen;

            $scope.mediaList = resp.media;
            for (let media of $scope.mediaList) {
                if (!media.sourceDownloadUrl && media.sourceUri.startsWith('data:')) {
                    media.sourceDownloadUrl = media.sourceUri;
                }
            }
        });
    }
    updateMarkup();
}
]);
