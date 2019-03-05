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
import org.opencastproject.util.RestUtil;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.lang3.StringUtils;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BoundParameterQuery;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.json.simple.JSONObject;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.stream.Collectors;

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
public class StatisticsEndpoint implements ManagedService {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(StatisticsEndpoint.class);

  private static final String KEY_INFLUX_URI = "influx.uri";
  private static final String KEY_INFLUX_USER = "influx.username";
  private static final String KEY_INFLUX_PW = "influx.password";
  private static final String KEY_INFLUX_DB = "influx.db";
  private static final String KEY_INFLUX_VIEWS_MEASUREMENT = "influx.measurement.views";

  private String influxUri = "http://127.0.0.1:8086";
  private String influxUser = "root";
  private String influxPw = "root";
  private String influxDbName = "opencast";
  private String influxViewsMeasurement = "impressions";

  private InfluxDB influxDB;

  private SecurityService securityService;
  private IndexService indexService;
  private AdminUISearchIndex searchIndex;

  enum DataResolution {
    HOURLY("1h"),
    DAILY("1d"),
    WEEKLY("1w"),
    MONTHLY("30d");

    private String influxUnit;

    DataResolution(String influxUnit) {
      this.influxUnit = influxUnit;
    }

    public String getInfluxUnit() {
      return influxUnit;
    }

    public static DataResolution fromString(final String value) {
      return DataResolution.valueOf(value.toUpperCase());
    }

    public static boolean isValid(final String value) {
      return Arrays.stream(DataResolution.values()).map(Enum::toString).anyMatch(v -> v.equals(value.toUpperCase()));
    }
  }

  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  public void setIndexService(IndexService indexService) {
    this.indexService = indexService;
  }

  public void setSearchIndex(AdminUISearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  @Override
  public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
    if (dictionary == null) {
      logger.info("No configuration available, using defaults");
    } else {
      final Object influxUriValue = dictionary.get(KEY_INFLUX_URI);
      if (influxUriValue != null) {
        influxUri = influxUriValue.toString();
      }
      final Object influxUserValue = dictionary.get(KEY_INFLUX_USER);
      if (influxUserValue != null) {
        influxUser = influxUserValue.toString();
      }
      final Object influxPwValue = dictionary.get(KEY_INFLUX_PW);
      if (influxPwValue != null) {
        influxPw = influxPwValue.toString();
      }
      final Object influxDbValue = dictionary.get(KEY_INFLUX_DB);
      if (influxDbValue != null) {
        influxDbName = influxDbValue.toString();
      }
      final Object influxViewsMeasurementValue = dictionary.get(KEY_INFLUX_VIEWS_MEASUREMENT);
      if (influxViewsMeasurementValue != null) {
        influxViewsMeasurement = influxViewsMeasurementValue.toString();
      }
    }
    connectInflux();
  }

  @GET
  @Path("views/event/{eventId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getviewsbyeventid", description = "Returns the views statistics for the given event as JSON", returnDescription = "The views statistics as JSON", pathParameters = {
    @RestParameter(name = "eventId", description = "The event id", isRequired = true, type = RestParameter.Type.STRING),
    @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly'", isRequired = true, type = STRING),
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
    if (StringUtils.isBlank(resolution) || !DataResolution.isValid(resolution)) {
      return RestUtil.R.badRequest("'resolution' must be one of 'hourly', 'daily', 'weekly', 'monthly'");
    }
    checkMediapackageAccess(eventId);
    final String influxUnit = DataResolution.fromString(resolution).getInfluxUnit();
    final Query query = BoundParameterQuery.QueryBuilder
      .newQuery("SELECT SUM(value) FROM " + influxViewsMeasurement + " WHERE episodeId=$eventId AND time>=$from AND time<$to GROUP BY time(" + influxUnit + ")")
      .forDatabase(influxDbName)
      .bind("eventId", eventId)
      .bind("from", from)
      .bind("to", to)
      .create();

    final QueryResult results = influxDB.query(query);
    return Response.ok(viewsToJson(results).toJSONString()).build();
  }


  @GET
  @Path("views/series/{seriesId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getviewsbyseriesid", description = "Returns the views statistics for the given series as JSON", returnDescription = "The views statistics as JSON", pathParameters = {
    @RestParameter(name = "seriesId", description = "The series id", isRequired = true, type = RestParameter.Type.STRING),
    @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly'", isRequired = true, type = STRING),
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
    if (StringUtils.isBlank(resolution) || !DataResolution.isValid(resolution)) {
      return RestUtil.R.badRequest("'resolution' must be one of 'hourly', 'daily', 'weekly', 'monthly'");
    }
    checkSeriesAccess(seriesId);
    final String influxUnit = DataResolution.fromString(resolution).getInfluxUnit();
    final Query query = BoundParameterQuery.QueryBuilder
      .newQuery("SELECT SUM(value) FROM " + influxViewsMeasurement + " WHERE seriesId=$seriesId AND time>=$from AND time<$to GROUP BY time(" + influxUnit + ")")
      .forDatabase(influxDbName)
      .bind("seriesId", seriesId)
      .bind("from", from)
      .bind("to", to)
      .create();

    final QueryResult results = influxDB.query(query);
    return Response.ok(viewsToJson(results).toJSONString()).build();
  }


  @GET
  @Path("views/organization/{organizationId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getviewsbyorganizationid", description = "Returns the views statistics for the given organization as JSON", returnDescription = "The views statistics as JSON", pathParameters = {
    @RestParameter(name = "organizationId", description = "The organization id", isRequired = true, type = RestParameter.Type.STRING),
    @RestParameter(name = "from", description = "Start of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "to", description = "End of the time series as ISO 8601 UTC date string", isRequired = true, type = STRING),
    @RestParameter(name = "resolution", description = "Data aggregation level. Must be one of 'hourly', 'daily', 'weekly', 'monthly'", isRequired = true, type = STRING),
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
    if (StringUtils.isBlank(resolution) || !DataResolution.isValid(resolution)) {
      return RestUtil.R.badRequest("'resolution' must be one of 'hourly', 'daily', 'weekly', 'monthly'");
    }
    checkOrganizationAccess(organizationId);
    final String influxUnit = DataResolution.fromString(resolution).getInfluxUnit();
    final Query query = BoundParameterQuery.QueryBuilder
      .newQuery("SELECT SUM(value) FROM " + influxViewsMeasurement + " WHERE organizationId=$organizationId AND time>=$from AND time<$to GROUP BY time(" + influxUnit + ")")
      .forDatabase(influxDbName)
      .bind("organizationId", organizationId)
      .bind("from", from)
      .bind("to", to)
      .create();

    final QueryResult results = influxDB.query(query);
    return Response.ok(viewsToJson(results).toJSONString()).build();
  }

  @SuppressWarnings("unchecked")
  private static JSONObject viewsToJson(final QueryResult results) {
    final List<String> labels = new ArrayList<>();
    final List<Double> values = new ArrayList<>();
    for (final QueryResult.Result result : results.getResults()) {
      if (result.getSeries() == null || result.getSeries().isEmpty()) {
        continue;
      }
      labels.addAll(result.getSeries().get(0).getValues().stream()
        .map(l -> (String) l.get(0))
        .collect(Collectors.toList()));
      values.addAll(result.getSeries().get(0).getValues().stream()
        .map(l -> l.get(1))
        .map(v -> v == null ? 0 : (Double) v)
        .collect(Collectors.toList()));
    }

    final JSONObject json = new JSONObject();
    json.put("labels", labels);
    json.put("values", values);
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

  private void connectInflux() {
    if (influxDB != null) {
      influxDB.close();
    }
    influxDB = InfluxDBFactory.connect(influxUri, influxUser, influxPw);
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
