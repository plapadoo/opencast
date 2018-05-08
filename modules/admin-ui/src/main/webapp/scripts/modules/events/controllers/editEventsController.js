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

// Controller for the "edit scheduled events" wizard
angular.module('adminNg.controllers')
    .controller('EditEventsCtrl', ['$scope', 'Table', 'Notifications', 'EventBulkEditResource', 'decorateWithTableRowSelection',
function ($scope, Table, Notifications, EventBulkEditResource, decorateWithTableRowSelection) {
    $scope.rows = Table.copySelected();
    $scope.allSelected = true; // by default, all rows are selected
    $scope.test = false;
    $scope.currentForm = 'generalForm';

    $scope.saveField = function(arg, arg2) {
        console.log('saving '+JSON.stringify(arg)+' '+JSON.stringify(arg2));
    };

    $scope.metadataRows = [{
        id: "title",
        label: "EVENTS.EVENTS.DETAILS.METADATA.TITLE",
        readOnly: false,
        required: true,
        type: "text",
        value: "Tears of Steel (No. 1)"
    }];

    $scope.valid = function () {
        return $scope.getSelectedIds().length > 0;
    };

    var onSuccess = function () {
        $scope.submitButton = false;
        $scope.close();
        // Notifications.add('success', 'TASK_CREATED');
        Table.deselectAll();
    };

    var onFailure = function () {
        $scope.submitButton = false;
        $scope.close();
        // Notifications.add('error', 'TASK_NOT_CREATED', 'global', -1);
    };

    $scope.submitButton = false;
    $scope.submit = function () {
        $scope.submitButton = true;
        if ($scope.valid()) {
            EventBulkEditResource.save(payload, onSuccess, onFailure);
        }
    };
    decorateWithTableRowSelection($scope);
}]);
