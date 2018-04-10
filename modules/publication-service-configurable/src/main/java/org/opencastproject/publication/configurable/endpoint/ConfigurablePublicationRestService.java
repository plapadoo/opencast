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
package org.opencastproject.publication.configurable.endpoint;

import org.opencastproject.job.api.JaxbJob;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobProducer;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.publication.api.ConfigurablePublicationService;
import org.opencastproject.rest.AbstractJobProducerEndpoint;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Rest endpoint, mainly for publishing media to a configurable channel
 */
@Path("/")
@RestService(name = "configurablepublicationservice", title = "Configurable Publication Service", abstractText =
        "This service publishes and retracts media package elements to a configurable channel", notes = { "All paths above are "
        + "relative to the REST endpoint base (something like http://your.server/files).  If the service is down "
        + "or not working it will return a status 503, this means the the underlying service is not working and is "
        + "either restarting or has failed. A status code 500 means a general failure has occurred which is not "
        + "recoverable and was not anticipated. In other words, there is a bug!" })
public class ConfigurablePublicationRestService extends AbstractJobProducerEndpoint {
  private static final String SEPARATOR = ";;";
  private static final Pattern SEPARATE_PATTERN = Pattern.compile(SEPARATOR);

  private static final Logger logger = LoggerFactory.getLogger(ConfigurablePublicationRestService.class);

  private ConfigurablePublicationService service;
  private ServiceRegistry serviceRegistry;

  public void setService(final ConfigurablePublicationService service) {
    this.service = service;
  }

  public void setServiceRegistry(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @Override
  public JobProducer getService() {
    // The implementation is, of course, resolved by OSGi, so to be "clean", we hold a referenc to just the interface
    // in this class, but at _this_ point, we assume it at least implements JobProducer.
    return (JobProducer) this.service;
  }

  @Override
  public ServiceRegistry getServiceRegistry() {
    return this.serviceRegistry;
  }

  private Collection<String> unflattenString(final String s) {
    return SEPARATE_PATTERN.splitAsStream(s).collect(Collectors.toList());
  }

  @POST
  @Path("/replace")
  @Produces(MediaType.TEXT_XML)
  @RestQuery(name = "replace", description = "Replace a media package in this publication channel", returnDescription = "The job that can be used to track the publication", restParameters = {
          @RestParameter(name = "mediapackage", isRequired = true, description = "The media package", type = RestParameter.Type.TEXT),
          @RestParameter(name = "channel", isRequired = true, description = "The channel name", type = RestParameter.Type.STRING),
          @RestParameter(name = "addElements", isRequired = true, description =
                  "The additional elements to published, separated by '" + SEPARATOR
                          + "'", type = RestParameter.Type.STRING),
          @RestParameter(name = "retractElements", isRequired = true, description =
                  "The element IDs to be retracted from the media package, separated by '" + SEPARATOR
                          + "'", type = RestParameter.Type.STRING) }, reponses = {
          @RestResponse(responseCode = HttpServletResponse.SC_OK, description = "An XML representation of the publication job") })
  public Response replace(@FormParam("mediapackage") final String mediaPackageXml,
          @FormParam("channel") final String channel, @FormParam("addElements") final String addElementsXml,
          @FormParam("retractElements") final String retractElementsFlattened) throws Exception {
    final Job job;
    try {
      final MediaPackage mediaPackage = MediaPackageParser.getFromXml(mediaPackageXml);
      final Collection<? extends MediaPackageElement> addElements = new HashSet<>(
              MediaPackageElementParser.getArrayFromXml(addElementsXml));
      final Collection<String> retractElements = unflattenString(retractElementsFlattened);
      job = service.replace(mediaPackage, channel, addElements, retractElements);
    } catch (IllegalArgumentException e) {
      logger.warn("Unable to create a publication job", e);
      return Response.status(Response.Status.BAD_REQUEST).build();
    } catch (Exception e) {
      logger.warn("Error publishing element", e);
      return Response.serverError().build();
    }
    return Response.ok(new JaxbJob(job)).build();
  }
}
