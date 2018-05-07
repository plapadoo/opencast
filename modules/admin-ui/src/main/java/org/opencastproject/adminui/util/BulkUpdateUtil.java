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
    private final List<String> eventIds;
    private final String title;
    private final String seriesId;
    private final String location;
    private final String start;
    private final String end;


    public BulkUpdateInstructions(String json) throws IllegalArgumentException {
      //TODO
      eventIds = null;
      title = null;
      seriesId = null;
      location = null;
      start = null;
      end = null;
    }

    public List<String> getEventIds() {
      return eventIds;
    }

    public String getTitle() {
      return title;
    }

    public String getLocation() {
      return location;
    }

    public String getSeriesId() {
      return seriesId;
    }

    public String getStart() {
      return start;
    }

    public String getEnd() {
      return end;
    }
  }

}
