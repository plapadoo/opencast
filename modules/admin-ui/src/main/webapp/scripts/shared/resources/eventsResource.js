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

angular.module('adminNg.resources')
.factory('EventsResource', ['$resource', 'Language', '$translate', 'ResourceHelper', function ($resource, Language, $translate, ResourceHelper) {

    /*
     * Here's an example for how we can fetch mock data from the server:
     * ...
     * return $resource('events/events.json', {}, {
     * ...,
     * this resource does not have a leading slash, hence the mock-data will be fetched from admin-ng/events/events.json
     *
     * In order to fetch real data, just add a leading slash:
     * ...
     * return $resource('/events/events.json', {}, {
     * ...,
     * then the real data will be fetched from /events/events.json
     */

    // We are live and are getting the real thing.
    return $resource('/admin-ng/event/:id', { id: '@id' }, {
        query: {method: 'GET', params: { id: 'events.json' }, isArray: false, transformResponse: function (data) {
            return ResourceHelper.parseResponse(data, function (r) {
                console.log("eventsresource r");
                console.log(r);
                var row = {};
                row.id = r.id;
                row.title = r.title;
                row.presenter = r.presenters.join(', ');
                row.technical_presenter = r.technical_presenters.join(', ');
                if (angular.isDefined(r.series)) {
                    row.series_name = r.series.title;
                    row.series_id = r.series.id;
                }
                row.review_status = r.review_status;
                row.event_status_raw = r.event_status;
                $translate(r.event_status).then(function (translation) {
                	row.event_status = translation;
                });
                row.source = r.source;
                row.scheduling_status = r.scheduling_status;
                $translate(r.scheduling_status).then(function (translation) {
                    row.scheduling_status = translation;
                });
                row.workflow_state = r.workflow_state;
                row.start_date = Language.formatDate('shortDate', r.start_date);
                console.log("row start_date: " + row.start_date);
                row.end_date = Language.formatDate('shortDate', r.end_date);
                row.publications = r.publications;
                row.start_time = Language.formatTime('shortTime', r.start_date);
                row.end_time = Language.formatTime('shortTime', r.end_date);
                row.technical_start = r.technical_start;
                row.technical_end = r.technical_end;
                row.has_comments = r.has_comments;
                row.has_open_comments = r.has_open_comments;
                row.needs_cutting = r.needs_cutting;
                row.has_preview = r.has_preview;
                row.location = r.location;
                row.agent_id = r.agent_id;
                row.managed_acl = r.managedAcl;
                row.type = "EVENT";
                return row;
            });

        }}
    });
}]);
