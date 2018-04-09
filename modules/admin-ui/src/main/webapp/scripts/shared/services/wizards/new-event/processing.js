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

angular.module('adminNg.services')
.factory('NewEventProcessing', ['$sce', '$timeout', 'NewEventProcessingResource', function ($sce, $timeout, NewEventProcessingResource) {
    var Processing = function (use) {
        // Update the content of the configuration panel with the given HTML
        var me = this, queryParams,
            updateConfigurationPanel = function (html) {
                console.log('update workflow configuration panel');
                if (angular.isUndefined(html)) {
                    html = '';
                }
                me.workflowConfiguration = $sce.trustAsHtml(html);
            },
            isWorkflowSet = function () {
                return angular.isDefined(me.ud.workflow) && angular.isDefined(me.ud.workflow.id);
            },
            idConfigElement = '#new-event-workflow-configuration',
            workflowConfigEl = angular.element(idConfigElement),
            originalValues = {};

        this.isProcessingState = true;
        this.ud = {};
        this.ud.workflow = {};

        // Object used for the workflow configurations
        window.ocWorkflowPanel = {};

        // Load all the workflows definitions
        if (use === 'tasks') {
            queryParams = {
                    tags: 'archive'
                };
        } else if (use === 'delete-event') {
            queryParams = {
                    tags: 'delete'
            };
        } else {
            queryParams = {
                tags: 'upload,schedule'
            };

        }
        NewEventProcessingResource.get(queryParams, function (data) {

            me.changingWorkflow = true;

            me.workflows = data.workflows;
            var default_workflow_id = data.default_workflow_id;

            // set default workflow as selected
            if(angular.isDefined(default_workflow_id)){

                for(var i = 0; i < me.workflows.length; i += 1){
                    var workflow = me.workflows[i];

                    if (workflow.id === default_workflow_id){
                      me.ud.workflow = workflow;
                      updateConfigurationPanel(me.ud.workflow.configuration_panel);
                      me.save();
                      break;
                    }
                }
            }
          me.changingWorkflow = false;

        });

        // Gather returned workflow properties into an array for easier usage later
         // (ignoring mediapackage IDs)
        function gatherEventProperties(workflowProperties, selectedIds) {
          var eventProperties = [];
          for (var i in workflowProperties) {
            if (!i.startsWith("$") && workflowProperties.hasOwnProperty(i)) {
              if (selectedIds.indexOf(i) >= 0) {
                eventProperties.push(workflowProperties[i]);
              }
            }
          }
          return eventProperties;
        }

        function valueOrAmbiguousCheckbox(eventProperties, idAttr) {
          var globalWorkflowAttr = null;
          var globalWorkflowAmbiguous = false;
          for (var i = 0; i < eventProperties.length; i++) {
            var workflowConfig = eventProperties[i];
            if(workflowConfig.hasOwnProperty(idAttr)) {
              var workflowAttr = workflowConfig[idAttr];
              console.log('Workflow has this attribute: '+workflowAttr);
              if (angular.isDefined(workflowAttr)) {
                // First workflow, just assign
                if (globalWorkflowAttr === null) {
                  console.log('Setting initial value');
                  globalWorkflowAttr = workflowAttr;
                }
                // Not the first workflow, and different attribute
                else if (globalWorkflowAttr !== workflowAttr) {
                  console.log('Value is ambiguous');
                  return { attr: null, defined: false };
                }
                // Otherwise, next workflow has the same value as previous
              }
            }
          }
          // We have to return an object here, since "null" could otherwise mean ambiguous _or_ none of the
          // events has this property.
          return { attr: globalWorkflowAttr, defined: true };
        }

        // Check all selected events for the specified property. If all events have the same value for the property,
        // return that value (which could be null, in the case that all events miss the property). Otherwise, return
        // a value indicating ambiguity.
        function valueOrAmbiguousText(eventProperties, idAttr) {
          var globalWorkflowAttr = null;
          var globalWorkflowAmbiguous = false;
          for (var i = 0; i < eventProperties.length; i++) {
            var p = eventProperties[i];

            if(!p.hasOwnProperty(idAttr))
              continue;

            var workflowAttr = p[idAttr];

            // First workflow, just assign
            if (globalWorkflowAttr === null) {
              console.log('Setting initial (text) value to '+workflowAttr);
              globalWorkflowAttr = workflowAttr;
            }
            // Not the first workflow, and different attribute
            else if (globalWorkflowAttr !== workflowAttr) {
              console.log('Text value is ambiguous');
              return { attr: null, defined: false };
            }
          }

          // We have to return an object here, since "null" could otherwise mean ambiguous _or_ none of the
          // events has this property.
          return { attr: globalWorkflowAttr, defined: true };
        }

        function valueOrAmbiguousRadio(eventProperties, radios) {
          var globalWorkflowAttr = null;
          var globalWorkflowAmbiguous = false;
          for (var ridx = 0; ridx < radios.length; ridx++) {
            var radio = radios[ridx];
            var rid = angular.element(radio).attr('id');
            for (var i = 0; i < eventProperties.length; i++) {
              var p = eventProperties[i];
              if(!p.hasOwnProperty(rid) || p[rid] !== 'true')
                continue;
              // First workflow, just assign
              if (globalWorkflowAttr === null) {
                console.log('Setting initial value to '+rid);
                globalWorkflowAttr = rid;
              }
              // Not the first workflow, and different attribute
              else if (globalWorkflowAttr !== rid) {
                console.log('Value is ambiguous');
                return { attr: null, defined: false };
              }
            }
          }

          // We have to return an object here, since "null" could otherwise mean ambiguous _or_ none of the
          // events has this property.
          return { attr: globalWorkflowAttr, defined: true };
        }

        // Listener for the workflow selection
        this.changeWorkflow = function (workflowProperties, selectedIds) {
            console.log('changing workflow, selected ids: '+JSON.stringify(selectedIds));
            originalValues = {};
            me.changingWorkflow = true;
            workflowConfigEl = angular.element(idConfigElement);
            if (angular.isDefined(me.ud.workflow)) {
                updateConfigurationPanel(me.ud.workflow.configuration_panel);
            } else {
                updateConfigurationPanel();
            }
            // Timeout because manipulating the just set HTML doesn't work otherwise
            $timeout(function() {
              var element, isRendered = workflowConfigEl.find('.configField').length > 0;
              if (!isRendered) {
                element = angular.element(me.ud.workflow.configuration_panel).find('.configField');
              } else {
                element = workflowConfigEl.find('.configField');
              }

              // Gather Array of Workflowproperties, we don't need the MP-IDs
              var eventProperties = gatherEventProperties(workflowProperties, selectedIds);

              var processedRadioNames = [];
              // Iterate over every input field, setting the previous values, if present.
              element.each(function (idx, el) {
                var e = angular.element(el);
                var idAttr = e.attr('id');

                console.log('Checking input field "'+JSON.stringify(idAttr)+'"');
                // Ignore input fields that don't have an ID
                if (!angular.isDefined(idAttr)) {
                  return;
                }

                if (e.is('[type=text]')) {
                  originalValues[idAttr] = e.val();

                  var globalWorkflowAttr = valueOrAmbiguousText(eventProperties, idAttr);

                  if (!globalWorkflowAttr.defined) {
                    console.log('text value is ambiguous, emptying');
                    e.val('');
                  } else {
                    // Only set it if we have a real value. If none of the workflows knows the property,
                    // we keep the input field at the default state.
                    if (globalWorkflowAttr.attr !== null) {
                      e.val(globalWorkflowAttr.attr);
                    }
                  }
                } else if (e.is('[type=radio]')) {
                  originalValues[idAttr] = e.attr('checked');

                  // Radio input fields all have different IDs, but the same name. Since we iterate over IDs but want to
                  // treat radio fields as a group, we have to keep track of which radio input elements we already
                  // processed.
                  var radioName = e.attr('name');
                  // We already processed this radio element?
                  if (processedRadioNames.indexOf(radioName) !== -1) {
                    console.log("element is a radio button "+radioName+", and we've already processed it: "+JSON.stringify(processedRadioNames));
                    return;
                  }
                  console.log("element is a radio button with name "+radioName+", didn't process that");

                  var radios = workflowConfigEl.find('input[name='+radioName+']');
                  var globalWorkflowAttr = valueOrAmbiguousRadio(eventProperties, radios);

                  // For ambiguous radio buttons, set them all to false.
                  if (!globalWorkflowAttr.defined) {
                    console.log('value is ambiguous, unchecking all');
                    radios.each(function (ridx, radio) {
                      angular.element(radio).attr('checked', false);
                    });
                  } else if (globalWorkflowAttr.attr !== null) {
                    console.log('value is '+globalWorkflowAttr.attr+', checking just that');
                    // If we've positively detected some setting, set that
                    radios.each(function (ridx, radio) {
                      var r = angular.element(radio);
                      r.attr('checked', r.attr('id') === globalWorkflowAttr.attr);
                    });
                  } else {
                    console.log('value is unknown, leaving it be');
                  }
                  // Otherwise, we use the default setting from the XML

                  processedRadioNames.push(radioName);
                } else if (e.is('[type=checkbox]')) {
                  originalValues[idAttr] = e.is(':checked') ? 'true' : 'false';

                  var globalWorkflowAttr = valueOrAmbiguousCheckbox(eventProperties, idAttr);
                  if (!globalWorkflowAttr.defined) {
                    e.prop("indeterminate", true);
                  } else {
                    // Only set it if we have a real value. If none of the workflows knows the property,
                    // we keep the checkbox at the default state
                    if (globalWorkflowAttr.attr !== null) {
                      e.attr('checked', globalWorkflowAttr.attr === 'true');
                    }
                  }
                }
              });
            });

            me.save();
            me.changingWorkflow = false;
        };

        // This is used for the new task post request
        this.getWorkflowConfigs = function (workflowProperties, selectedIds) {
            var workflowConfigs = {}, element, isRendered = workflowConfigEl.find('.configField').length > 0;

            if (!isRendered) {
                element = angular.element(me.ud.workflow.configuration_panel).find('.configField');
            } else {
                element = workflowConfigEl.find('.configField');
            }

            var eventProperties = gatherEventProperties(workflowProperties, selectedIds);
            var resultConfigs = {};
            // Iterate over each event, configuring it separately
            for (var i in workflowProperties) {
              if (i.startsWith("$") || !workflowProperties.hasOwnProperty(i)) {
                continue;
              }

              console.log("Constructing properties of "+i+" (selected ids "+JSON.stringify(selectedIds)+")");

              var workflowConfig = workflowProperties[i];
              var resultConfig = {};

              // Iterate over each input field
              element.each(function (idx, el) {
                var e = angular.element(el);

                var idAttr = e.attr('id');
                // Ignore input fields that don't have an ID
                if (!angular.isDefined(idAttr)) {
                  return;
                }

                console.log('Checking input field "'+JSON.stringify(idAttr)+'"');


                if (e.is('[type=text]')) {
                  if (e.val() !== '') {
                    resultConfig[idAttr] = e.val();
                  } else {
                    var globalWorkflowAttr = valueOrAmbiguousText(eventProperties, idAttr);

                    if (!globalWorkflowAttr.defined) {
                      console.log('Field is ambiguous, checking workflow props');
                      var workflowValue = workflowConfig[idAttr];
                      if (angular.isDefined(workflowValue)) {
                        console.log('Workflow value is '+workflowValue+' using that');
                        resultConfig[idAttr] = workflowValue;
                      } else {
                        originalValue = originalValues[idAttr];
                        console.log('Workflow value is undefined, using standard value: '+originalValue);
                        resultConfig[idAttr] = originalValue;
                      }
                    } else {
                      if (globalWorkflowAttr.attr === null) {
                        var originalValue = originalValues[idAttr];
                        console.log('Field is non-ambiguous, but unset, using standard value: '+originalValue);
                        resultConfig[idAttr] = originalValue;
                      } else {
                        console.log('Field is non-ambiguous, setting '+globalWorkflowAttr.attr);
                        resultConfig[idAttr] = globalWorkflowAttr.attr;
                      }
                    }
                  }
                } else if (e.is('[type=checkbox]')) {
                  if (!e.prop('indeterminate')) {
                    if (e.is(':checked'))
                      resultConfig[idAttr] = 'true';
                    else
                      resultConfig[idAttr] = 'false';
                  } else {
                    var globalWorkflowAttr = valueOrAmbiguousCheckbox(eventProperties, idAttr);

                    if (!globalWorkflowAttr.defined) {
                      console.log('Field is ambiguous, checking workflow props');
                      var workflowValue = workflowConfig[idAttr];
                      if (angular.isDefined(workflowValue)) {
                        console.log('Workflow value is '+workflowValue+' using that');
                        resultConfig[idAttr] = workflowValue;
                      } else {
                        var originalValue = originalValues[idAttr];
                        console.log('Workflow value is undefined, using standard value: '+originalValue);
                        resultConfig[idAttr] = originalValue;
                      }
                    } else {
                      if (globalWorkflowAttr.attr === null) {
                        var originalValue = originalValues[idAttr];
                        console.log('Field is non-ambiguous, but unset, using standard value: '+originalValue);
                        resultConfig[idAttr] = originalValue;
                      } else {
                        console.log('Field is non-ambiguous, setting '+globalWorkflowAttr.attr);
                        resultConfig[idAttr] = globalWorkflowAttr.attr;
                      }
                    }
                  }
                } else if (e.is('[type=radio]')) {
                  var radioName = e.attr('name');
                  var radios = workflowConfigEl.find('input[name='+radioName+']');
                  var globalWorkflowAttr = valueOrAmbiguousRadio(eventProperties, radios);

                  var radioIds = [];
                  var setByUser = false;
                  for (var ridx = 0; ridx < radios.length; ridx++) {
                    var radio = angular.element(radios[ridx]);
                    radioIds.push(radio.attr('id'));
                    if (radio.is(':checked')) {
                      resultConfig[radio.attr('id')] = "true";
                      setByUser = true;
                    } else {
                      resultConfig[radio.attr('id')] = "false";
                    }
                  }

                  if (setByUser) {
                    return;
                  }

                  if (!globalWorkflowAttr.defined) {
                    console.log('Field is ambiguous, checking workflow props');

                    var finalValue = { attribute: null, defined: false };
                    for (var ridx = 0; ridx < radioIds.length; ridx++) {
                      var rid = radioIds[ridx];

                      var workflowValue = workflowConfig[rid];
                      // This is our guy, a radio value that's true.
                      if (angular.isDefined(workflowValue) && workflowValue === "true") {
                        // But we might have two radio values that are true. Damn!
                        if (finalValue.defined === true) {
                          // Set defined to false, treat this as "value wasn't specified at all"
                          finalValue.defined = false;
                          finalValue.attribute = null;
                          break;
                        } else {
                          finalValue.defined = true;
                          finalValue.attribute = rid;
                        }
                      }
                    }

                    // No value found? Then use the default value
                    if (!finalValue.defined) {
                      for (var ridx = 0; ridx < radioIds.length; ridx++) {
                        var rid = radioIds[ridx];

                        if (originalValues[rid] === "true") {
                          finalValue.defined = "true";
                          finalValue.attribute = rid;
                          break;
                        }
                      }

                      if (finalValue.defined !== "true") {
                        console.log("shit, no default value?");
                      }
                    }

                    for (var ridx = 0; ridx < radioIds.length; ridx++) {
                      var rid = radioIds[ridx];

                      if (finalValue.defined && finalValue.attribute === rid)
                        resultConfig[rid] = "true";
                      else
                        resultConfig[rid] = "false";
                    }
                  } else {
                    if (globalWorkflowAttr.attr === null) {
                      console.log('Field is non-ambiguous, but unset, using standard value: '+originalValue);
                      for (var ridx = 0; ridx < radioIds.length; ridx++) {
                        var rid = radioIds[ridx];

                        if (angular.isDefined(originalValues[rid]) && originalValues[rid] === "true") {
                          resultConfig[rid] = "true";
                        } else {
                          resultConfig[rid] = "false";
                        }
                      }
                    } else {
                      console.log('Field is non-ambiguous, setting '+globalWorkflowAttr.attr);
                      for (var ridx = 0; ridx < radioIds.length; ridx++) {
                        var rid = radioIds[ridx];

                        if (rid == globalWorkflowAttr.attr) {
                          resultConfig[rid] = "true";
                        } else {
                          resultConfig[rid] = "false";
                        }
                      }
                    }
                  }
                }
              });

              resultConfigs[i] = resultConfig;
            }

            console.log('workflow configs: '+JSON.stringify(resultConfigs))

            return resultConfigs;
        }

        // Get the workflow configuration (used for the final value table in the wizard)
        this.getWorkflowConfig = function () {
            var workflowConfig = {}, element, isRendered = workflowConfigEl.find('.configField').length > 0;

            if (!isRendered) {
                element = angular.element(me.ud.workflow.configuration_panel).find('.configField');
            } else {
                element = workflowConfigEl.find('.configField');
            }

            element.each(function (idx, el) {
                var element = angular.element(el);

                var id = element.attr('id');
                if (angular.isDefined(id)) {
                    if (element.is('[type=checkbox]')) {
                      if (element.prop('indeterminate')) {
                        workflowConfig[id] = '*';
                      } else {
                        workflowConfig[id] = element.is(':checked') ? 'true' : 'false';
                      }
                    } else if (element.is('[type=radio]')) {
                      var radioName = element.attr('name');
                      var radios = workflowConfigEl.find('input[name='+radioName+']');
/*                      console.log('=> radioName: '+radioName);*/
                      var ambiguous = true;
                      radios.each(function (ridx, radio) {
                        var r = angular.element(radio);
                        if (r.is(':checked')) {
/*                          console.log('=> is checked: '+r.attr('checked'));*/
                          ambiguous = false;
                        }
                      });
/*                      console.log('=> done');*/
                      if (ambiguous) {
                        workflowConfig[id] = '*';
                      } else {
                        workflowConfig[id] = element.is(':checked') ? 'true' : 'false';
                      }
                    } else {
                      // TODO: Properly check ambiguity
                      if (element.val() === '')
                        workflowConfig[id] = '*';
                      else
                        workflowConfig[id] = element.val();
                    }
                }
            });

            return workflowConfig;
        };

        this.isValid = function () {
            if (isWorkflowSet()) {
                return true;
            } else {
                return false;
            }
        };

        // Save the workflow configuration
        this.save = function () {

            if (isWorkflowSet()) {
                me.ud.workflow.selection  = {
                    id: me.ud.workflow.id,
                    configuration: me.getWorkflowConfig()
                };
            }
        };

        this.reset = function () {
            me.isProcessingState = true;
            me.ud = {};
            me.ud.workflow = {};
            me.workflows = {};
        };

        this.getUserEntries = function () {
            return me.ud.workflow;
        };
    };

    return {
        get: function (use) {
            return new Processing(use);
        }
    };

}]);
