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

package org.opencastproject.statistics.impl;

import org.opencastproject.statistics.api.StatisticsService;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BoundParameterQuery;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements {@link StatisticsService}. Uses influxdb for permanent storage.
 */
public class StatisticsServiceImpl implements StatisticsService, ManagedService {

  /** Logging utility */
  private static final Logger logger = LoggerFactory.getLogger(StatisticsServiceImpl.class);

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

  public void activate(ComponentContext cc) {
    logger.info("Activating Statistics Service");
  }

  @Override
  public void updated(Dictionary<String, ?> dictionary) {
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


  @Override
  public Views getEpisodeViews(String episodeId, Instant from, Instant to, DataResolution resolution) {
    final String influxUnit = dataResolutionToInfluxUnit(resolution);
    final Query query = BoundParameterQuery.QueryBuilder
        .newQuery("SELECT SUM(value) FROM " + influxViewsMeasurement + " WHERE episodeId=$eventId AND time>=$from AND time<$to GROUP BY time(" + influxUnit + ")")
        .forDatabase(influxDbName)
        .bind("eventId", episodeId)
        .bind("from", from)
        .bind("to", to)
        .create();

    final QueryResult results = influxDB.query(query);
    return queryResultToViews(results);
  }

  @Override
  public Views getSeriesViews(String seriesId, Instant from, Instant to, DataResolution resolution) {
    final String influxUnit = dataResolutionToInfluxUnit(resolution);
    final Query query = BoundParameterQuery.QueryBuilder
        .newQuery("SELECT SUM(value) FROM " + influxViewsMeasurement + " WHERE seriesId=$seriesId AND time>=$from AND time<$to GROUP BY time(" + influxUnit + ")")
        .forDatabase(influxDbName)
        .bind("seriesId", seriesId)
        .bind("from", from)
        .bind("to", to)
        .create();

    final QueryResult results = influxDB.query(query);
    return queryResultToViews(results);
  }

  @Override
  public Views getOrganizationViews(String organizationId, Instant from, Instant to, DataResolution resolution) {
    final String influxUnit = dataResolutionToInfluxUnit(resolution);
    final Query query = BoundParameterQuery.QueryBuilder
        .newQuery("SELECT SUM(value) FROM " + influxViewsMeasurement + " WHERE organizationId=$organizationId AND time>=$from AND time<$to GROUP BY time(" + influxUnit + ")")
        .forDatabase(influxDbName)
        .bind("organizationId", organizationId)
        .bind("from", from)
        .bind("to", to)
        .create();

    final QueryResult results = influxDB.query(query);
    return queryResultToViews(results);
  }

  private void connectInflux() {
    if (influxDB != null) {
      influxDB.close();
    }
    influxDB = InfluxDBFactory.connect(influxUri, influxUser, influxPw);
  }

  private static String dataResolutionToInfluxUnit(DataResolution dataResolution) {
    switch (dataResolution) {
      case HOURLY:
        return "1h";
      case DAILY:
        return "1d";
      case WEEKLY:
        return "1w";
      case MONTHLY:
        return "30d";
      case YEARLY:
        return "360d";
      default:
        throw new IllegalArgumentException("unmapped DataResultion: " + dataResolution.name());
    }
  }

  private Views queryResultToViews(QueryResult results) {
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
    return new Views(labels, values);
  }

}
