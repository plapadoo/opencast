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

package org.opencastproject.statistics.endpoint;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;

import org.opencastproject.rest.RestConstants;
import org.opencastproject.statistics.api.StatisticsService;
import org.opencastproject.systems.OpencastConstants;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.json.simple.JSONObject;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST endpoint for Statistics Service.
 *
 */
@Path("/")
@RestService(name = "statisticsservice", title = "Statistics Service", abstractText = "This service provides statistics.", notes = {
    "All paths above are relative to the REST endpoint base (something like http://your.server/files)",
    "If the service is down or not working it will return a status 503, this means the the underlying service is "
        + "not working and is either restarting or has failed",
    "A status code 500 means a general failure has occurred which is not recoverable and was not anticipated. In "
        + "other words, there is a bug! You should file an error report with your server logs from the time when the "
        + "error occurred: <a href=\"https://opencast.jira.com\">Opencast Issue Tracker</a>"})
public class StatisticsRestService {

  /** Logging utility */
  private static final Logger logger = LoggerFactory.getLogger(StatisticsRestService.class);

  /** Series Service */
  private StatisticsService statisticsService;

  /** Default server URL */
  protected String serverUrl = "http://localhost:8080";

  /** Service url */
  protected String serviceUrl = null;

  /**
   * OSGi callback for setting statistics service.
   *
   * @param statisticsService
   */
  public void setService(StatisticsService statisticsService) {
    this.statisticsService = statisticsService;
  }

  /**
   * Activates REST service.
   *
   * @param cc
   *          ComponentContext
   */
  public void activate(ComponentContext cc) {
    if (cc == null) {
      this.serverUrl = "http://localhost:8080";
    } else {
      String ccServerUrl = cc.getBundleContext().getProperty(OpencastConstants.SERVER_URL_PROPERTY);
      logger.debug("Configured server url is {}", ccServerUrl);
      if (ccServerUrl == null)
        this.serverUrl = "http://localhost:8080";
      else {
        this.serverUrl = ccServerUrl;
      }
    }
    serviceUrl = (String) cc.getProperties().get(RestConstants.SERVICE_PATH_PROPERTY);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("views/episode/{episodeID:.+}.json")
  @RestQuery(name = "getAsJson", description = "Returns the views for the given episodeID", returnDescription = "Returns the views JSON document",
      pathParameters = {@RestParameter(name = "episodeID", isRequired = true, description = "The episode identifier", type = STRING)},
      restParameters = {
          @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
          @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
          @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly', 'yearly'", isRequired = true, type = STRING),
      },
      reponses = {
          @RestResponse(responseCode = SC_OK, description = "The episode views.")
      })
  public Response getEpisodeViews(
      @PathParam("eventID") final String eventID,
      @QueryParam("from") final String from,
      @QueryParam("to") final String to,
      @QueryParam("resolution") final String resolution
  ) {
    try {
      final StatisticsService.Views views = statisticsService.getEpisodeViews(
          eventID,
          Instant.parse(from),
          Instant.parse(to),
          StatisticsService.DataResolution.fromString(resolution)
      );
      return Response.ok(viewsToJson(views).toJSONString()).build();
    } catch (Exception e) {
      logger.error("Could not retrieve episode views: {}", e.getMessage());
      throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("views/series/{seriesID:.+}.json")
  @RestQuery(name = "getAsJson", description = "Returns the views for the given seriesID", returnDescription = "Returns the views JSON document",
      pathParameters = {@RestParameter(name = "seriesID", isRequired = true, description = "The series identifier", type = STRING)},
      restParameters = {
          @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
          @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
          @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly', 'yearly'", isRequired = true, type = STRING),
      },
      reponses = {
          @RestResponse(responseCode = SC_OK, description = "The series views.")})
  public Response getSeriesViews(
      @PathParam("seriesID") String seriesID,
      @QueryParam("from") final String from,
      @QueryParam("to") final String to,
      @QueryParam("resolution") final String resolution) {
    try {
      final StatisticsService.Views views = statisticsService.getSeriesViews(
          seriesID,
          Instant.parse(from),
          Instant.parse(to),
          StatisticsService.DataResolution.fromString(resolution)
      );
      return Response.ok(viewsToJson(views).toJSONString()).build();
    } catch (Exception e) {
      logger.error("Could not retrieve series views: {}", e.getMessage());
      throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("views/organization/{organizationID:.+}.json")
  @RestQuery(name = "getAsJson", description = "Returns the views for the given organizationID", returnDescription = "Returns the views JSON document",
      pathParameters = {@RestParameter(name = "organizationID", isRequired = true, description = "The organization identifier", type = STRING)},
      restParameters = {
          @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
          @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
          @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly', 'yearly'", isRequired = true, type = STRING),
      },
      reponses = {
          @RestResponse(responseCode = SC_OK, description = "The organization views.")})
  public Response getOrganizationViews(
      @PathParam("organizationID") String organizationID,
      @QueryParam("from") final String from,
      @QueryParam("to") final String to,
      @QueryParam("resolution") final String resolution
  ) {
    try {
      final StatisticsService.Views views = statisticsService.getOrganizationViews(
          organizationID,
          Instant.parse(from),
          Instant.parse(to),
          StatisticsService.DataResolution.fromString(resolution)
      );
      return Response.ok(viewsToJson(views).toJSONString()).build();
    } catch (Exception e) {
      logger.error("Could not retrieve organization views: {}", e.getMessage());
      throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }


  @SuppressWarnings("unchecked")
  private static JSONObject viewsToJson(final StatisticsService.Views views) {
    final JSONObject json = new JSONObject();
    json.put("labels", views.getLabels());
    json.put("values", views.getValues());
    return json;
  }

}
