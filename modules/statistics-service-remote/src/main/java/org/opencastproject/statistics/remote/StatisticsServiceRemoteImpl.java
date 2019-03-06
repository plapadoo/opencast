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

package org.opencastproject.statistics.remote;

import static javax.servlet.http.HttpServletResponse.SC_OK;

import org.opencastproject.serviceregistry.api.RemoteBase;
import org.opencastproject.statistics.api.StatisticsService;
import org.opencastproject.util.doc.rest.RestService;


import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Path;

/**
 * A proxy to a remote series service.
 */
@Path("/")
@RestService(name = "statisticsservice", title = "Statistics Service Remote", abstractText = "This service provides statistics.", notes = {
        "All paths above are relative to the REST endpoint base (something like http://your.server/files)",
        "If the service is down or not working it will return a status 503, this means the the underlying service is "
                + "not working and is either restarting or has failed",
        "A status code 500 means a general failure has occurred which is not recoverable and was not anticipated. In "
                + "other words, there is a bug! You should file an error report with your server logs from the time when the "
                + "error occurred: <a href=\"https://opencast.jira.com\">Opencast Issue Tracker</a>" })
public class StatisticsServiceRemoteImpl extends RemoteBase implements StatisticsService {

  private static final Logger logger = LoggerFactory.getLogger(StatisticsServiceRemoteImpl.class);

  public StatisticsServiceRemoteImpl() {
    super(JOB_TYPE);
  }

  @Override
  public Views getEpisodeViews(String episodeId, Instant from, Instant to, DataResolution resolution) {
    final HttpGet get = new HttpGet("views/episode/" + episodeId + ".json");
    final HttpResponse response = getResponse(get, SC_OK);
    try {
      if (response != null) {
        return jsonToViews(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      closeConnection(response);
    }
    throw new RuntimeException("Unable to get views from remote service");
  }

  @Override
  public Views getSeriesViews(String seriesId, Instant from, Instant to, DataResolution resolution) {
    final HttpGet get = new HttpGet("views/series/" + seriesId + ".json");
    final HttpResponse response = getResponse(get, SC_OK);
    try {
      if (response != null) {
        return jsonToViews(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      closeConnection(response);
    }
    throw new RuntimeException("Unable to get views from remote service");
  }

  @Override
  public Views getOrganizationViews(String organizationId, Instant from, Instant to, DataResolution resolution) {
    final HttpGet get = new HttpGet("views/organization/" + organizationId + ".json");
    final HttpResponse response = getResponse(get, SC_OK);
    try {
      if (response != null) {
        return jsonToViews(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      closeConnection(response);
    }
    throw new RuntimeException("Unable to get views from remote service");
  }

  private Views jsonToViews(String json) throws ParseException, JSONException {
    final JSONParser parser = new JSONParser();
    final JSONObject jsonObject = (JSONObject) parser.parse(json);
    final JSONArray labelsJson = jsonObject.getJSONArray("labels");
    final JSONArray valuesJson = jsonObject.getJSONArray("values");
    final List<String> labels = new ArrayList<>();
    final List<Double> values = new ArrayList<>();
    for (int i = 0; i < labelsJson.length(); i++) {
      labels.add(labelsJson.getString(i));
      values.add(valuesJson.getDouble(i));
    }
    return new Views(labels, values);
  }
}
