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

/* global $, Mustache */
/* eslint no-console: "warn" */

'use strict';

function refreshTable() {
  $.getJSON('/lti/events/jobs', function( eventList ) {
    var listTemplate = $('#template-upload-list').html();

    // render episode view
    $('#processed-table').html(
      Mustache.render(
        listTemplate,
        { events: eventList, hasProcessing: eventList.length > 0 }));

    window.setTimeout(refreshTable, 5000);
  });
}

function getParam(name) {
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has(name)) {
    return urlParams.get(name);
  }
  return '';
}

function loadPage() {
  // load spinner
  $('upload-form').html($('#template-loading').html());

  var uploadTemplate = $('#template-upload-dialog').html(),
      tpldata = { seriesName: getParam('series_name'), series: getParam('series') };

  // render template
  $('#upload-form').html(Mustache.render(uploadTemplate, tpldata));

  refreshTable();
}


$(document).ready(function() {
  loadPage();
});
