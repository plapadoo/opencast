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

package org.opencastproject.adminui.endpoint;

import static org.opencastproject.security.api.SecurityConstants.GLOBAL_ADMIN_ROLE;
import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;

import org.opencastproject.adminui.index.AdminUISearchIndex;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.index.service.impl.index.series.Series;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.api.User;
import org.opencastproject.statistics.api.StatisticsService;
import org.opencastproject.util.RestUtil;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("/")
@RestService(name = "statistics", title = "statistics façade service",
  abstractText = "Provides statistics",
  notes = {"This service provides statistics."
    + "<em>This service is for exclusive use by the module admin-ui. Its API might change "
    + "anytime without prior notice. Any dependencies other than the admin UI will be strictly ignored. "
    + "DO NOT use this for integration of third-party applications.<em>"})
public class StatisticsEndpoint {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(StatisticsEndpoint.class);

  private SecurityService securityService;
  private IndexService indexService;
  private AdminUISearchIndex searchIndex;
  private StatisticsService statisticsService;

  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  public void setIndexService(IndexService indexService) {
    this.indexService = indexService;
  }

  public void setSearchIndex(AdminUISearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  public void setStatisticsService(StatisticsService statisticsService) {
    this.statisticsService = statisticsService;
  }


  @GET
  @Path("views/event/{eventId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getviewsbyeventid", description = "Returns the views statistics for the given event as JSON", returnDescription = "The views statistics as JSON", pathParameters = {
    @RestParameter(name = "eventId", description = "The event id", isRequired = true, type = RestParameter.Type.STRING)},
    restParameters = {
    @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly', 'yearly'", isRequired = true, type = STRING),
  },
    reponses = {
      @RestResponse(description = "Returns the views statistics for the given event as JSON", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "No event with this identifier was found.", responseCode = HttpServletResponse.SC_NOT_FOUND),
      @RestResponse(description = "If the current user is not authorized to perform this action", responseCode = HttpServletResponse.SC_UNAUTHORIZED)
    })
  public Response getEventViews(
    @PathParam("eventId") final String eventId,
    @QueryParam("from") final String from,
    @QueryParam("to") final String to,
    @QueryParam("resolution") final String resolution
  ) throws Exception {
    if (StringUtils.isBlank(eventId)) {
      return RestUtil.R.badRequest("Missing value for 'eventId'");
    }
    if (StringUtils.isBlank(from) || !isIso8601Utc(from)) {
      return RestUtil.R.badRequest("Missing value for 'from' or not in ISO 8601 UTC format");
    }
    if (StringUtils.isBlank(to) || !isIso8601Utc(to)) {
      return RestUtil.R.badRequest("Missing value for 'to' or not in ISO 8601 UTC format");
    }
    if (to.compareTo(from) <= 0) {
      return RestUtil.R.badRequest("'from' date must be before 'to' date");
    }
    if (StringUtils.isBlank(resolution) || !StatisticsService.DataResolution.isValid(resolution)) {
      return RestUtil.R.badRequest("'resolution' must be one of 'hourly', 'daily', 'weekly', 'monthly'");
    }
    checkMediapackageAccess(eventId);
    final StatisticsService.Views results = statisticsService.getEpisodeViews(
      eventId, Instant.parse(from), Instant.parse(to), StatisticsService.DataResolution.fromString(resolution)
    );
    return Response.ok(viewsToJson(results).toJSONString()).build();
  }


  @GET
  @Path("views/series/{seriesId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getviewsbyseriesid", description = "Returns the views statistics for the given series as JSON", returnDescription = "The views statistics as JSON", pathParameters = {
    @RestParameter(name = "seriesId", description = "The series id", isRequired = true, type = RestParameter.Type.STRING)},
    restParameters = {
    @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly', 'yearly'", isRequired = true, type = STRING),
  },
    reponses = {
      @RestResponse(description = "Returns the views statistics for the given series as JSON", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "No series with this identifier was found.", responseCode = HttpServletResponse.SC_NOT_FOUND),
      @RestResponse(description = "If the current user is not authorized to perform this action", responseCode = HttpServletResponse.SC_UNAUTHORIZED)
    })
  public Response getSeriesViews(
    @PathParam("seriesId") final String seriesId,
    @QueryParam("from") final String from,
    @QueryParam("to") final String to,
    @QueryParam("resolution") final String resolution
  ) throws Exception {
    if (StringUtils.isBlank(seriesId)) {
      return RestUtil.R.badRequest("Missing value for 'seriesId'");
    }
    if (StringUtils.isBlank(from) || !isIso8601Utc(from)) {
      return RestUtil.R.badRequest("Missing value for 'from' or not in ISO 8601 UTC format");
    }
    if (StringUtils.isBlank(to) || !isIso8601Utc(to)) {
      return RestUtil.R.badRequest("Missing value for 'to' or not in ISO 8601 UTC format");
    }
    if (to.compareTo(from) <= 0) {
      return RestUtil.R.badRequest("'from' date must be before 'to' date");
    }
    if (StringUtils.isBlank(resolution) || !StatisticsService.DataResolution.isValid(resolution)) {
      return RestUtil.R.badRequest("'resolution' must be one of 'hourly', 'daily', 'weekly', 'monthly'");
    }
    checkSeriesAccess(seriesId);
    final StatisticsService.Views results = statisticsService.getSeriesViews(
      seriesId, Instant.parse(from), Instant.parse(to), StatisticsService.DataResolution.fromString(resolution)
    );
    return Response.ok(viewsToJson(results).toJSONString()).build();
  }


  @GET
  @Path("views/organization/{organizationId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getviewsbyorganizationid", description = "Returns the views statistics for the given organization as JSON", returnDescription = "The views statistics as JSON", pathParameters = {
    @RestParameter(name = "organizationId", description = "The organization id", isRequired = true, type = RestParameter.Type.STRING)},
    restParameters = {
    @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly', 'yearly'", isRequired = true, type = STRING),
  },
    reponses = {
      @RestResponse(description = "Returns the views statistics for the given organization as JSON", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "No organization with this identifier was found.", responseCode = HttpServletResponse.SC_NOT_FOUND),
      @RestResponse(description = "If the current user is not authorized to perform this action", responseCode = HttpServletResponse.SC_UNAUTHORIZED)
    })
  public Response getOrganizationViews(
    @PathParam("organizationId") final String organizationId,
    @QueryParam("from") final String from,
    @QueryParam("to") final String to,
    @QueryParam("resolution") final String resolution
  ) throws Exception {
    if (StringUtils.isBlank(organizationId)) {
      return RestUtil.R.badRequest("Missing value for 'organizationId'");
    }
    if (StringUtils.isBlank(from) || !isIso8601Utc(from)) {
      return RestUtil.R.badRequest("Missing value for 'from' or not in ISO 8601 UTC format");
    }
    if (StringUtils.isBlank(to) || !isIso8601Utc(to)) {
      return RestUtil.R.badRequest("Missing value for 'to' or not in ISO 8601 UTC format");
    }
    if (to.compareTo(from) <= 0) {
      return RestUtil.R.badRequest("'from' date must be before 'to' date");
    }
    if (StringUtils.isBlank(resolution) || !StatisticsService.DataResolution.isValid(resolution)) {
      return RestUtil.R.badRequest("'resolution' must be one of 'hourly', 'daily', 'weekly', 'monthly'");
    }
    checkOrganizationAccess(organizationId);
    final StatisticsService.Views results = statisticsService.getOrganizationViews(
      organizationId, Instant.parse(from), Instant.parse(to), StatisticsService.DataResolution.fromString(resolution)
    );
    return Response.ok(viewsToJson(results).toJSONString()).build();
  }

  @SuppressWarnings("unchecked")
  private static JSONObject viewsToJson(final StatisticsService.Views views) {
    final JSONObject json = new JSONObject();
    json.put("labels", views.getLabels());
    json.put("values", views.getValues());
    return json;
  }

  private static boolean isIso8601Utc(final String value) {
    try {
      Instant.parse(value);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private void checkMediapackageAccess(final String mpId) throws UnauthorizedException, SearchIndexException {
    final Opt<Event> event = indexService.getEvent(mpId, searchIndex);
    if (event.isNone()) {
      // IndexService checks permissions and returns None if user is unauthorized
      throw new UnauthorizedException(securityService.getUser(), "read");
    }
  }

  private void checkSeriesAccess(final String seriesId) throws UnauthorizedException, SearchIndexException {
    final Opt<Series> series = indexService.getSeries(seriesId, searchIndex);
    if (series.isNone()) {
      // IndexService checks permissions and returns None if user is unauthorized
      throw new UnauthorizedException(securityService.getUser(), "read");
    }
  }

  private void checkOrganizationAccess(final String orgId) throws UnauthorizedException {
    final User currentUser = securityService.getUser();
    final Organization currentOrg = securityService.getOrganization();
    final String currentOrgAdminRole = currentOrg.getAdminRole();
    final String currentOrgId = currentOrg.getId();

    boolean authorized = currentUser.hasRole(GLOBAL_ADMIN_ROLE)
      || (currentUser.hasRole(currentOrgAdminRole) && currentOrgId.equals(orgId));

    if (!authorized) {
      throw new UnauthorizedException(currentUser, "read");
    }
  }

}
