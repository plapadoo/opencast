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

import org.opencastproject.util.RestUtil;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.apache.commons.lang3.StringUtils;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Dictionary;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;

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

  private String influxUri = "http://127.0.0.1:8086";
  private String influxUser = "root";
  private String influxPw = "root";

  private InfluxDB influxDB;

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
      @RestResponse(description = "No event with this identifier was found.", responseCode = HttpServletResponse.SC_NOT_FOUND)
    })
  public Response getEventMetadata(
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

    //TODO: Security. hat $user Zugriff auf event?
    return null;
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
      @RestResponse(description = "No series with this identifier was found.", responseCode = HttpServletResponse.SC_NOT_FOUND)
    })
  public Response getSeriesMetadata(
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

    //TODO: Security. hat $user Zugriff auf series?
    return null;
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
      @RestResponse(description = "No organization with this identifier was found.", responseCode = HttpServletResponse.SC_NOT_FOUND)
    })
  public Response getOrganizationMetadata(
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
    //TODO: Security. hat $user Zugriff auf organization?
    return null;
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
}
