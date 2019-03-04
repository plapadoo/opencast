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

import org.opencastproject.util.doc.rest.RestService;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;

import javax.ws.rs.Path;

@Path("/")
@RestService(name = "statistics", title = "statistics façade service",
  abstractText = "Provides statistics",
  notes = { "This service provides statistics."
              + "<em>This service is for exclusive use by the module admin-ui. Its API might change "
              + "anytime without prior notice. Any dependencies other than the admin UI will be strictly ignored. "
              + "DO NOT use this for integration of third-party applications.<em>"})
public class StatisticsEndpoint  implements ManagedService {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(StatisticsEndpoint.class);

  private static final String KEY_INFLUX_URI = "influx.uri";
  private static final String KEY_INFLUX_USER = "influx.username";
  private static final String KEY_INFLUX_PW = "influx.password";

  private String influxUri = "http://127.0.0.1:8086";
  private String influxUser = "root";
  private String influxPw = "root";

  private InfluxDB influxDB;

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

  private void connectInflux() {
    if (influxDB != null) {
      influxDB.close();
    }
    influxDB = InfluxDBFactory.connect(influxUri, influxUser, influxPw);
  }
}
