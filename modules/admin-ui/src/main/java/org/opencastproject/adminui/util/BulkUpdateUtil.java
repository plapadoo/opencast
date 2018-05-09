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

package org.opencastproject.adminui.util;

import static org.opencastproject.adminui.endpoint.AbstractEventEndpoint.SCHEDULING_AGENT_ID_KEY;
import static org.opencastproject.adminui.endpoint.AbstractEventEndpoint.SCHEDULING_END_KEY;
import static org.opencastproject.adminui.endpoint.AbstractEventEndpoint.SCHEDULING_START_KEY;

import org.opencastproject.adminui.impl.index.AdminUISearchIndex;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.catalog.adapter.events.CommonEventCatalogUIAdapter;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.mediapackage.MediaPackageElements;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


public final class BulkUpdateUtil {

  private static final JSONParser JSON_PARSER = new JSONParser();

  private BulkUpdateUtil() {
  }

  public static Optional<Event> getEvent(
    final IndexService indexSvc,
    final AdminUISearchIndex index,
    final String id) {
    try {
      final Event event = indexSvc.getEvent(id, index).orNull();
      return Optional.ofNullable(event);
    } catch (SearchIndexException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static String addSchedulingDates(
    final Event event,
    final String schedulingJson)
    throws IllegalArgumentException {
    try {
      final JSONObject scheduling = (JSONObject) JSON_PARSER.parse(schedulingJson);
      ZonedDateTime startDate = ZonedDateTime.parse(event.getRecordingStartDate());
      ZonedDateTime endDate = ZonedDateTime.parse(event.getRecordingEndDate());
      final ZoneId timezone = ZoneId.of((String) scheduling.get("timezone"));
      if (scheduling.containsKey(SCHEDULING_START_KEY)) {
        startDate = adjustedSchedulingDate(scheduling, SCHEDULING_START_KEY,
          startDate.withZoneSameInstant(timezone).withHour(0).withMinute(0));
      }
      if (scheduling.containsKey(SCHEDULING_END_KEY)) {
        endDate = adjustedSchedulingDate(scheduling, SCHEDULING_END_KEY,
          endDate.withZoneSameInstant(timezone).withHour(0).withMinute(0));
      }
      if (endDate.isBefore(startDate)) {
        endDate = endDate.plusDays(1);
      }
      if (scheduling.containsKey("duration")) {
        endDate = adjustedSchedulingDate(scheduling, "duration", startDate);
      }
      if (scheduling.containsKey("weekday")) {
        final String weekdayAbbrev = ((String) scheduling.get("weekday"));
        final DayOfWeek newWeekDay = Arrays.stream(DayOfWeek.values())
          .filter(d -> d.name().startsWith(weekdayAbbrev.toUpperCase()))
          .findAny()
          .orElseThrow(() -> new IllegalArgumentException("Cannot parse weekday: " + weekdayAbbrev));
        final int daysDiff = newWeekDay.getValue() - startDate.getDayOfWeek().getValue();
        startDate = startDate.plusDays(daysDiff);
        endDate = endDate.plusDays(daysDiff);
      }
      scheduling.put(SCHEDULING_START_KEY, startDate.format(DateTimeFormatter.ISO_INSTANT));
      scheduling.put(SCHEDULING_END_KEY, endDate.format(DateTimeFormatter.ISO_INSTANT));
      return scheduling.toJSONString();
    } catch (ParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static String toNonTechnicalMetadataJson(final String schedulingJson) throws IllegalArgumentException {
    try {
      final JSONObject scheduling = (JSONObject) JSON_PARSER.parse(schedulingJson);
      final List<JSONObject> fields = new ArrayList<>();
      if (scheduling.containsKey(SCHEDULING_AGENT_ID_KEY)) {
        final JSONObject locationJson = new JSONObject();
        locationJson.put("id", "location");
        locationJson.put("value", scheduling.get(SCHEDULING_AGENT_ID_KEY));
        fields.add(locationJson);
      }
      if (scheduling.containsKey(SCHEDULING_START_KEY) && scheduling.containsKey(SCHEDULING_END_KEY)) {
        final JSONObject startDateJson = new JSONObject();
        startDateJson.put("id", "startDate");
        String startDate = Instant.parse((String) scheduling.get(SCHEDULING_START_KEY))
          .atOffset(ZoneOffset.UTC)
          .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ".000Z";
        startDateJson.put("value", startDate);
        fields.add(startDateJson);

        final JSONObject durationJson = new JSONObject();
        durationJson.put("id", "duration");
        final Instant start = Instant.parse((String) scheduling.get(SCHEDULING_START_KEY));
        final Instant end = Instant.parse((String) scheduling.get(SCHEDULING_END_KEY));
        final Duration duration = Duration.between(start, end);
        final long hours = duration.toHours();
        final long minutes = duration.minusHours(hours).toMinutes();
        final long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        durationJson.put("value", String.format("%02d:%02d:%02d", hours, minutes, seconds));
        fields.add(durationJson);
      }

      final JSONObject result = new JSONObject();
      result.put("flavor", MediaPackageElements.EPISODE.toString());
      result.put("title", CommonEventCatalogUIAdapter.EPISODE_TITLE);
      result.put("fields", fields);
      return result.toJSONString();
    } catch (ParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static String mergeMetadataFields(String first, String second) {
    try {
      if (first == null) return JSONArray.toJSONString(Collections.singletonList(JSON_PARSER.parse(second)));
      if (second == null) return JSONArray.toJSONString(Collections.singletonList(JSON_PARSER.parse(first)));
      final JSONObject firstJson = (JSONObject) JSON_PARSER.parse(first);
      final JSONObject secondJson = (JSONObject) JSON_PARSER.parse(second);
      JSONArray fields = ((JSONArray) firstJson.get("fields"));
      fields.addAll((JSONArray) secondJson.get("fields"));
      return JSONArray.toJSONString(Collections.singletonList(firstJson));
    } catch (ParseException | ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static ZonedDateTime adjustedSchedulingDate(
    final JSONObject scheduling,
    final String dateKey,
    final ZonedDateTime date) {
    final JSONObject time = (JSONObject) scheduling.get(dateKey);
    final int hour = Math.toIntExact((Long) time.get("hour"));
    final int minute = Math.toIntExact((Long) time.get("minute"));
    return date.plusHours(hour).plusMinutes(minute).withZoneSameInstant(ZoneOffset.UTC);
  }

  public static class BulkUpdateInstructions {
    private static final String KEY_EVENTS = "events";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_SCHEDULING = "scheduling";

    private final List<String> eventIds;
    private final String metadata;
    private final String scheduling;

    @SuppressWarnings("unchecked")
    public BulkUpdateInstructions(final String json) throws IllegalArgumentException {
      try {
        final JSONObject jsonObject = (JSONObject) JSON_PARSER.parse(json);
        eventIds = (JSONArray) jsonObject.get(KEY_EVENTS);
        metadata = Optional.ofNullable(jsonObject.get(KEY_METADATA))
          .map(o -> ((JSONObject) o).toJSONString()).orElse(null);
        scheduling = Optional.ofNullable(jsonObject.get(KEY_SCHEDULING))
          .map(o -> ((JSONObject) o).toJSONString()).orElse(null);
      } catch (ParseException e) {
        throw new IllegalArgumentException(e);
      }
    }

    public List<String> getEventIds() {
      return eventIds;
    }

    public String getMetadata() {
      return metadata;
    }

    public String getScheduling() {
      return scheduling;
    }
  }

}
