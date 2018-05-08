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

import org.opencastproject.adminui.impl.index.AdminUISearchIndex;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.matterhorn.search.SearchIndexException;

import com.entwinemedia.fn.data.Opt;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.List;
import java.util.Optional;

public class BulkUpdateUtil {

  public static Optional<Event> getEvent(IndexService indexService, AdminUISearchIndex index, String id) {
    try {
      final Opt<Event> optEvent = indexService.getEvent(id, index);
      return optEvent.isSome() ? Optional.of(optEvent.get()) : Optional.empty();
    } catch (SearchIndexException e) {
      throw new RuntimeException(e);
    }
  }

  public static class BulkUpdateInstructions {
    private static final String KEY_EVENTS = "events";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_SCHEDULING = "scheduling";

    private final List<String> eventIds;
    private final String metadata;
    private final String scheduling;

    public BulkUpdateInstructions(String json) throws IllegalArgumentException {
      try {
        final JSONObject jsonObject = (JSONObject) new JSONParser().parse(json);
        eventIds = (JSONArray) jsonObject.get(KEY_EVENTS);
        metadata = Optional.ofNullable(jsonObject.get(KEY_METADATA)).map(Object::toString).orElse(null);
        scheduling = Optional.ofNullable(jsonObject.get(KEY_SCHEDULING)).map(Object::toString).orElse(null);
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
