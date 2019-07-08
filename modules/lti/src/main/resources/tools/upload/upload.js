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

/* global $, Mustache, i18ndata */
/* eslint no-console: "warn" */

'use strict';

function loadPage() {

  // load spinner
  $('main').html($('#template-loading').html());

  var template = $('#template-upload-dialog').html(),
      rendered = "",
      tpldata = {
      acl: JSON.stringify([
             {
               "action": "write",
               "role": "ROLE_ADMIN"
             },
             {
               "action": "read",
               "role": "ROLE_ADMIN"
             },
             {
               "action": "write",
               "role": "ROLE_OAUTH_USER"
             },
             {
               "action": "read",
               "role": "ROLE_OAUTH_USER"
             }
           ]),
      metadata: JSON.stringify([
                  {
                    "flavor": "dublincore/episode",
                    "fields": [
                      {
                        "id": "title",
                        "value": "Captivating title"
                      },
                      {
                        "id": "subjects",
                        "value": ["John Clark", "Thiago Melo Costa"]
                      },
                      {
                        "id": "description",
                        "value": "A great description"
                      },
                      {
                        "id": "startDate",
                        "value": "2016-06-22"
                      },
                      {
                        "id": "startTime",
                        "value": "13:30:00Z"
                      },
                      {
                        "id": "duration",
                        "value": "6000"
                      }
                    ]
                  }
                ]),
      schedule: JSON.stringify({}),
      processing: JSON.stringify({
                    "workflow": "fast",
                    "configuration": {
                      "flagForCutting": "false",
                      "flagForReview": "false",
                      "publishToEngage": "true",
                      "publishToHarvesting": "true",
                      "straightToPublishing": "true"
                    }
                  })
      };

  // render template
  rendered += Mustache.render(template, tpldata);

  // render episode view
  $('main').html(rendered);
}


$(document).ready(function() {
  loadPage();
});
