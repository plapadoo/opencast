/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
'use strict';

// Controller for all event screens.
angular.module('adminNg.controllers')
.controller('ToolsCtrl', ['$scope', '$route', '$location', 'Storage', '$window', 'ToolsResource', 'Notifications', 'EventHelperService',
    function ($scope, $route, $location, Storage, $window, ToolsResource, Notifications, EventHelperService) {
        var errorMessageId = null;

        $scope.navigateTo = function (path) {
            $location.path(path).replace();
        };

        $scope.event    = EventHelperService;
        $scope.resource = $route.current.params.resource;
        $scope.tab      = $route.current.params.tab;
        if ($scope.tab === "editor") {
          $scope.area   = "segments";
        }
        $scope.id       = $route.current.params.itemId;

        $scope.event.eventId = $scope.id;

        $scope.unsavedChanges = false;

        $scope.setChanges = function(changed) {
            $scope.unsavedChanges = changed;
        };

        $scope.openTab = function (tab) {
            $scope.tab = tab;
            if ($scope.tab === "editor") {
              $scope.area = "segments";
            }

            // This fixes a problem where video playback breaks after switching tabs. Changing the location seems
            // to be destructive to the <video> element working together with opencast's external controls.
            var lastRoute, off;
            lastRoute = $route.current;
            off = $scope.$on('$locationChangeSuccess', function () {
                $route.current = lastRoute;
                off();
            });

            $scope.navigateTo('/events/' + $scope.resource + '/' + $scope.id + '/tools/' + tab);
        };

        $scope.openArea = function (area) {
            $scope.area = area;
        };

        $scope.anyTrackSelected = function (type) {
            var selected = false;
            var present = false;
            for(var i = 0; i < $scope.video.source_tracks.length; i++) {
                var t = $scope.video.source_tracks[i][type];
                if (t.present === true) {
                    present = true;
                }
                if (t.present === true && t.hidden === false) {
                    selected = true;
                }
            }
            // If we don't have any tracks at all, selecting none is valid
            if (present === false) {
                return true;
            }
            return selected;
        };

        $scope.trackClicked = function(index, type) {
            $scope.video.source_tracks[index][type].hidden = !$scope.video.source_tracks[index][type].hidden;
        };

        $scope.trackHasPreview = function(index, type) {
            return $scope.video.source_tracks[index][type].preview_image !== null;
        };

        $scope.trackIndexToName = function(index) {
            var flavor = $scope.video.source_tracks[index].flavor;
            return flavor.type;
        };

        $scope.sanityCheckSourceTracks = function() {
            if (!$scope.anyTrackSelected('video')) {
                errorMessageId = Notifications.add('error', 'VIDEO_SOURCE_TRACKS_INVALID');
                return false;
            }
            return true;
        };

        // TODO Move the following to a VideoCtrl
        $scope.player = {};
        $scope.video  = ToolsResource.get({ id: $scope.id, tool: 'editor' });

        $scope.submitButton = false;

        $scope.submit = function () {
            if (!$scope.sanityCheckSourceTracks()) {
                return;
            }
            $scope.submitButton = true;
            $scope.video.$save({ id: $scope.id, tool: $scope.tab }, function () {
                $scope.submitButton = false;
                if ($scope.video.workflow) {
                    Notifications.add('success', 'VIDEO_CUT_PROCESSING');
                    $location.url('/events/' + $scope.resource);
                } else {
                    Notifications.add('success', 'VIDEO_CUT_SAVED');
                }
                $scope.unsavedChanges = false;
                if (errorMessageId !== null) {
                    Notifications.remove(errorMessageId);
                    errorMessageId = null;
                }
            }, function () {
                $scope.submitButton = false;
                errorMessageId = Notifications.add('error', 'VIDEO_CUT_NOT_SAVED', 'video-tools');
            });
        };

        $scope.leave = function () {
            Storage.put('pagination', $scope.resource, 'resume', true);
            $location.url('/events/' + $scope.resource);
        };
    }
]);
