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
    .controller('EditEventsCtrl', ['$scope', 'Table', 'Notifications', 'EventBulkEditResource', 'SeriesResource', 'CaptureAgentsResource', 'EventsSchedulingResource', 'JsHelper', 'SchedulingHelperService', 'WizardHandler', 'Language', 'decorateWithTableRowSelection',
function ($scope, Table, Notifications, EventBulkEditResource, SeriesResource, CaptureAgentsResource, EventsSchedulingResource, JsHelper, SchedulingHelperService, WizardHandler, Language, decorateWithTableRowSelection) {
    var me = this;
    var SCHEDULING_CONTEXT = 'event-scheduling';


    $scope.rows = Table.copySelected();
    $scope.eventSummaries = [];
    $scope.allSelected = true; // by default, all rows are selected
    $scope.test = false;
    $scope.currentForm = 'generalForm';
    /* Get the current client timezone */
    var tzOffset = (new Date()).getTimezoneOffset() / -60;
    $scope.tz = 'UTC' + (tzOffset < 0 ? '-' : '+') + tzOffset;

    // Get available series
    $scope.seriesResults = {};
    SeriesResource.query().$promise.then(function(results) {
        angular.forEach(results.rows, function(row) {
            $scope.seriesResults[row.title] = row.id;
        });
    });

    // Get available capture agents
    $scope.captureAgents = [];
    CaptureAgentsResource.query({inputs: true}).$promise.then(function (data) {
        $scope.captureAgents = data.rows;
    });

    function getMetadataPart(getter) {
        var result = null;
        for (var i = 0; i < $scope.rows.length; i++) {
            var row = $scope.rows[i];
            if (!row.selected) {
                continue;
            }
            var val = getter(row);
            if (result === null) {
                result = val;
            } else if (result !== val) {
                return null;
            }
        }
        return result;
    }

    function isSelected(id) {
        return JsHelper.arrayContains($scope.getSelectedIds(), id);
    }

    function getSchedulingPart(getter) {
        var result = { ambiguous: false, value: null };
        angular.forEach($scope.schedulingSingle, function(value) {
            if (!isSelected(value.eventId)) {
                return;
            }
            var val = getter(value);
            if (result.ambiguous === false) {
                if (result.value === null) {
                    result.value = val;
                } else if (result.value !== val) {
                    result.ambiguous = true;
                    result.value = null;
                }
            }
        });
        if (result.ambiguous === true) {
            return null;
        } else {
            return result.value;
        }
    }

    var fromJsWeekday = function(d) {
        // Javascript week days start at sunday (so 0=SU), so we have to roll over.
        return JsHelper.getWeekDays()[d + 1 % 7];
    };


    $scope.hours = JsHelper.initArray(24);
    $scope.minutes = JsHelper.initArray(60);
    $scope.weekdays = JsHelper.getWeekDays();

    // Get scheduling information for the events
    $scope.scheduling = {};
    $scope.schedulingSingle = EventsSchedulingResource.bulkGet(
        JsHelper.mapFunction($scope.rows, function(v) { return v.id; }));

    $scope.onTemporalValueChange = function(type) {
        console.log(JSON.stringify($scope.scheduling));
        SchedulingHelperService.applyTemporalValueChange($scope.scheduling, type, true);
    };

    this.clearConflicts = function () {
        $scope.conflicts = [];
        if (me.notificationConflict) {
            Notifications.remove(me.notificationConflict, SCHEDULING_CONTEXT);
            me.notifictationConflict = undefined;
        }
    };

    this.conflictsDetected = function (response) {
        me.clearConflicts();
        if (response.status === 409) {
            me.notificationConflict = Notifications.add('error', 'CONFLICT_DETECTED', SCHEDULING_CONTEXT);
            angular.forEach(response.data, function (data) {
                $scope.conflicts.push({
                    title: data.title,
                    start: Language.formatDateTime('medium', data.start),
                    end: Language.formatDateTime('medium', data.end)
                });
            });
        }
        $scope.checkingConflicts = false;
    };

    this.noConflictsDetected = function () {
        me.clearConflicts();
        $scope.checkingConflicts = false;
    };

    $scope.checkConflicts = function () {
        return new Promise(function(resolve, reject) {
            $scope.checkingConflicts = true;
            var payload = {
                eventIds: $scope.getSelectedIds(),
                scheduling: $scope.scheduling
            };
            EventsSchedulingResource.checkConflicts(payload, me.noConflictsDetected, me.conflictsDetected)
                .$promise.then(function() {
                    resolve();
                })
                .catch(function(err) {
                    reject();
                });
        });
    };

    $scope.checkingConflicts = false;

    var nextWizardStep = function() {
        WizardHandler.wizard("editEventsWz").next();  
    };

    // This is triggered after the user selected some events in the first wizard step
    $scope.clearFormAndContinue = function() {
        $scope.metadataRows = [
            {
                id: "title",
                label: "EVENTS.EVENTS.DETAILS.METADATA.TITLE",
                readOnly: false,
                required: true,
                type: "text",
                value: getMetadataPart(function(row) { return row.title; })
            },
            {
                id: "isPartOf",
                collection: $scope.seriesResults,
                label: "EVENTS.EVENTS.DETAILS.METADATA.SERIES",
                readOnly: false,
                required: false,
                translatable: false,
                type: "text",
                value: getMetadataPart(function(row) { return row.series_id; })
            },
        ];
        $scope.scheduling = {
            timezone: JsHelper.getTimeZoneName(),
            location: getMetadataPart(function(row) { return row.location; }),
            start: {
                date: getSchedulingPart(function(entry) { return entry.start.date; }),
                hour: getSchedulingPart(function(entry) { return entry.start.hour; }),
                minute: getSchedulingPart(function(entry) { return entry.start.minute; })
            },
            end: {
                date: getSchedulingPart(function(entry) { return entry.end.date; }),
                hour: getSchedulingPart(function(entry) { return entry.end.hour; }),
                minute: getSchedulingPart(function(entry) { return entry.end.minute; })
            },
            duration: {
                hour: getSchedulingPart(function(entry) { return entry.duration.hour; }),
                minute: getSchedulingPart(function(entry) { return entry.duration.minute; })
            },
            weekday: getSchedulingPart(function(entry) { return fromJsWeekday(new Date(entry.start.date).getDay()).key; })
        };
        nextWizardStep();
    };

    $scope.metadataRows = [];

    $scope.saveField = function(arg, callback) {
        // Müssen wir was machen wenn man das Editfeld verlässt? Merken obs dirty ist oder so?
    };

    $scope.valid = function () {
        return $scope.getSelectedIds().length > 0;
    };

    $scope.nonSchedule = function(row) {
        return row.source !== 'SCHEDULE';
    };

    $scope.nonScheduleSelected = function() {
        return JsHelper.filter($scope.getSelected(),function (r) { return r.source !== 'SCHEDULE'; }).length > 0;
    };

    $scope.rowsValid = function() {
        return !$scope.nonScheduleSelected() && $scope.hasAnySelected();
    };

    $scope.generateEventSummariesAndContinue = function() {
        $scope.eventSummaries = [];
        console.log('generating summaries');
        angular.forEach($scope.schedulingSingle, function(value) {
            if (!isSelected(value.eventId)) {
                return;
            }

            var changes = [];

            if ($scope.scheduling.location !== null && $scope.scheduling.location !== value.agentId) {
                changes.push(['EVENTS.EVENTS.TABLE.LOCATION', value.agentId, $scope.scheduling.location]);
            }

            var formatPart = function(schedObj, valObj, translation) {
                if (schedObj.hour !== null && schedObj.hour !== valObj.hour || schedObj.minute !== null && schedObj.minute !== valObj.minute) {
                    var oldTime = JsHelper.humanizeTime(valObj.hour, valObj.minute);
                    var newHour = valObj.hour;
                    if (schedObj.hour !== null && schedObj.hour !== valObj.hour) {
                        newHour = schedObj.hour;
                    }
                    var newMinute = valObj.minute;
                    if (schedObj.minute !== null && schedObj.minute !== valObj.minute) {
                        newMinute = schedObj.minute;
                    }
                    var newTime = JsHelper.humanizeTime(newHour, newMinute);
                    changes.push(['EVENTS.EVENTS.TABLE.'+translation, oldTime, newTime]);
                }
            };

            formatPart($scope.scheduling.start, value.start);
            formatPart($scope.scheduling.end, value.end);

            $scope.eventSummaries.push({
                title: "foo",
                changes: changes
            });
        });
        console.log('done, next step');
        nextWizardStep();
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
        // Deep copy scheduling, we need to change it for the request
        var scheduling = $.extend(true, {}, $scope.scheduling);
        JsHelper.removeNulls(scheduling);
        JsHelper.removeNulls(scheduling.start);
        JsHelper.removeNulls(scheduling.end);
        JsHelper.removeNulls(scheduling.duration);

        var payload = {
            metadata: {
                flavor: "dublincore/episode",
                title: "EVENTS.EVENTS.DETAILS.CATALOG.EPISODE",
                fields: JsHelper.filter($scope.metadataRows, function(row) { return angular.isDefined(row.value) && row.value !== null; })
            },
            scheduling: scheduling,
            eventIds: $scope.getSelectedIds()
        };
        if ($scope.valid()) {
            EventBulkEditResource.update(payload, onSuccess, onFailure);
        }
    };
    decorateWithTableRowSelection($scope);
}]);
