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

package org.opencastproject.statistics.api;


import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Statistics service API.
 *
 */
public interface StatisticsService {

  /**
   * Resolution to get data with (e.g. daily. weekly, ...)
   */
  enum DataResolution {
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    public static DataResolution fromString(final String value) {
      return DataResolution.valueOf(value.toUpperCase());
    }

    public static boolean isValid(final String value) {
      return Arrays.stream(DataResolution.values()).map(Enum::toString).anyMatch(v -> v.equals(value.toUpperCase()));
    }
  }

  /**
   * Views count result with labels and values. For each label, there is one value.
   */
  class Views {
    private List<String> labels;
    private List<Double> values;

    public Views(List<String> labels, List<Double> values) {
      this.labels = labels;
      this.values = values;
    }

    public List<String> getLabels() {
      return labels;
    }

    public List<Double> getValues() {
      return values;
    }
  }

  /**
   * Identifier for service registration and location
   */
  String JOB_TYPE = "org.opencastproject.statistics";

  /**
   * Get the views for the given episode
   *
   * @param episodeId
   *     The id of the episode to get the views for
   * @param from
   *     The start time (UTC)
   * @param to
   *     The end time (UTC)
   * @param resolution
   *     The resolution to get the views with
   *
   * @return The views
   */
  Views getEpisodeViews(String episodeId, Instant from, Instant to, DataResolution resolution);

  /**
   * Get the views for the given series
   *
   * @param seriesId
   *     The id of the series to get the views for
   * @param from
   *     The start time (UTC)
   * @param to
   *     The end time (UTC)
   * @param resolution
   *     The resolution to get the views with
   *
   * @return The views
   */
  Views getSeriesViews(String seriesId, Instant from, Instant to, DataResolution resolution);

  /**
   * Get the views for the given organization
   *
   * @param organizationId
   *     The id of the organization to get the views for
   * @param from
   *     The start time (UTC)
   * @param to
   *     The end time (UTC)
   * @param resolution
   *     The resolution to get the views with
   *
   * @return The views
   */
  Views getOrganizationViews(String organizationId, Instant from, Instant to, DataResolution resolution);


}
