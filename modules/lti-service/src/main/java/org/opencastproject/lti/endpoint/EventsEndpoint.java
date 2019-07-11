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
package org.opencastproject.lti.endpoint;

import static org.opencastproject.util.RestUtil.getEndpointUrl;
import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;

import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.catalog.adapter.MetadataList;
import org.opencastproject.index.service.impl.index.AbstractSearchIndex;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.index.service.impl.index.event.EventHttpServletRequest;
import org.opencastproject.index.service.impl.index.event.EventSearchQuery;
import org.opencastproject.ingest.api.IngestService;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.matterhorn.search.SearchResult;
import org.opencastproject.matterhorn.search.SearchResultItem;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogList;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.metadata.dublincore.EventCatalogUIAdapter;
import org.opencastproject.metadata.dublincore.MetadataCollection;
import org.opencastproject.metadata.dublincore.MetadataField;
import org.opencastproject.rest.RestConstants;
import org.opencastproject.scheduler.api.SchedulerException;
import org.opencastproject.security.api.AccessControlEntry;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.api.User;
import org.opencastproject.series.api.SeriesException;
import org.opencastproject.series.api.SeriesQuery;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.systems.OpencastConstants;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.RestUtil;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestParameter.Type;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;
import org.opencastproject.workflow.api.WorkflowDatabaseException;

import com.entwinemedia.fn.data.Opt;
import com.google.gson.Gson;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Path("/")
@RestService(name = "ltiservice", title = "LTI Service", notes = {}, abstractText = "Provides operations to LTI clients")
public class EventsEndpoint implements ManagedService {
  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(EventsEndpoint.class);

  /** Base URL of this endpoint */
  protected String endpointBaseUrl;

  /* OSGi service references */
  private IndexService indexService;
  private IngestService ingestService;
  private SecurityService securityService;
  private SeriesService seriesService;
  private AbstractSearchIndex abstractSearchIndex;

  private AbstractSearchIndex searchIndex;
  private String workflow;
  private String workflowConfiguration;
  private String retractWorkflowId;
  private final List<EventCatalogUIAdapter> catalogUIAdapters = new ArrayList<>();

  /** OSGi DI */
  public void setAbstractSearchIndex(AbstractSearchIndex abstractSearchIndex) {
    this.abstractSearchIndex = abstractSearchIndex;
  }

  /** OSGi DI */
  public void setSearchIndex(AbstractSearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  /** OSGi DI */
  public void setSeriesService(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  /** OSGi DI */
  public void setIndexService(IndexService indexService) {
    this.indexService = indexService;
  }

  /** OSGi DI */
  public void setIngestService(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  /** OSGi DI */
  void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /** OSGi DI. */
  public void addCatalogUIAdapter(EventCatalogUIAdapter catalogUIAdapter) {
    catalogUIAdapters.add(catalogUIAdapter);
  }

  /** OSGi DI. */
  public void removeCatalogUIAdapter(EventCatalogUIAdapter catalogUIAdapter) {
    catalogUIAdapters.remove(catalogUIAdapter);
  }

  private List<EventCatalogUIAdapter> getEventCatalogUIAdapters() {
    return new ArrayList<>(getEventCatalogUIAdapters(securityService.getOrganization().getId()));
  }

  public List<EventCatalogUIAdapter> getEventCatalogUIAdapters(String organization) {
    List<EventCatalogUIAdapter> adapters = new ArrayList<>();
    for (EventCatalogUIAdapter adapter : catalogUIAdapters) {
      if (organization.equals(adapter.getOrganization())) {
        adapters.add(adapter);
      }
    }
    return adapters;
  }

  /** OSGi activation method */
  void activate(ComponentContext cc) {
    logger.info("Activating External API - Events Endpoint");

    final Tuple<String, String> endpointUrl = getEndpointUrl(cc, OpencastConstants.EXTERNAL_API_URL_ORG_PROPERTY,
            RestConstants.SERVICE_PATH_PROPERTY);
    endpointBaseUrl = UrlSupport.concat(endpointUrl.getA(), endpointUrl.getB());
    logger.debug("Configured service endpoint is {}", endpointBaseUrl);
  }

  /** OSGi callback if properties file is present */
  @Override
  public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
    // Ensure properties is not null
    if (properties == null) {
      throw new IllegalArgumentException("No configuration specified for events endpoint");
    }
    String workflowStr = (String) properties.get("workflow");
    if (workflowStr == null) {
      throw new IllegalArgumentException("Configuration is missing 'workflow' parameter");
    }
    String workflowConfigurationStr = (String) properties.get("workflow-configuration");
    if (workflowConfigurationStr == null) {
      throw new IllegalArgumentException("Configuration is missing 'workflow-configuration' parameter");
    }
    try {
      new JSONParser().parse(workflowConfigurationStr);
      workflowConfiguration = workflowConfigurationStr;
      workflow = workflowStr;
      retractWorkflowId = (String) properties.get("retract-workflow-id");
    } catch (ParseException e) {
      throw new IllegalArgumentException("Invalid JSON specified for workflow configuration");
    }
  }

  private String getEventUrl(String eventId) {
    return UrlSupport.concat(endpointBaseUrl, eventId);
  }

  @GET
  @Path("/jobs")
  public Response listJobs() {
    final User user = securityService.getUser();
    final EventSearchQuery query = new EventSearchQuery(securityService.getOrganization().getId(), user)
            .withCreator(user.getName());
    try {
      SearchResult<Event> results = searchIndex.getByQuery(query);
      List<Map<String, String>> jsonResult = Arrays.stream(results.getItems())
              .map(SearchResultItem::getSource)
              .filter(e -> !e.getEventStatus().equals("EVENTS.EVENTS.STATUS.PROCESSED"))
              .map(e -> {
                Map<String, String> eventMap = new HashMap<>();
                eventMap.put("title", e.getTitle());
                eventMap.put("status", e.getEventStatus());
                return eventMap;
              })
              .collect(Collectors.toList());
      return Response.status(Status.OK).entity(new Gson().toJson(jsonResult, List.class)).build();
    } catch (SearchIndexException e) {
      logger.error("error searching",e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity("error while searching").build();
    }
  }

  @POST
  @Path("/")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @RestQuery(name = "createevent", description = "Creates an event by sending metadata, access control list, processing instructions and files in a multipart request.", returnDescription = "", restParameters = {
          @RestParameter(name = "acl", isRequired = false, description = "A collection of roles with their possible action", type = STRING),
          @RestParameter(name = "metadata", description = "Event metadata as Form param", isRequired = false, type = STRING),
          @RestParameter(name = "scheduling", description = "Scheduling information as Form param", isRequired = false, type = STRING),
          @RestParameter(name = "presenter", description = "Presenter movie track", isRequired = false, type = Type.FILE),
          @RestParameter(name = "presentation", description = "Presentation movie track", isRequired = false, type = Type.FILE),
          @RestParameter(name = "audio", description = "Audio track", isRequired = false, type = Type.FILE),
          @RestParameter(name = "processing", description = "Processing instructions task configuration", isRequired = false, type = STRING) }, reponses = {
                  @RestResponse(description = "A new event is created and its identifier is returned in the Location header.", responseCode = HttpServletResponse.SC_CREATED),
                  @RestResponse(description = "The event could not be created due to a scheduling conflict.", responseCode = HttpServletResponse.SC_CONFLICT),
                  @RestResponse(description = "The request is invalid or inconsistent..", responseCode = HttpServletResponse.SC_BAD_REQUEST) })
  public Response createNewEvent(@HeaderParam("Accept") String acceptHeader, @Context HttpServletRequest request) {
    try {
      final EventHttpServletRequest r = new EventHttpServletRequest();
      final MediaPackage mp = ingestService.createMediaPackage();
      if (mp == null) {
        logger.debug("Unable to create media package for event");
        return RestUtil.R.badRequest("Unable to create media package for event");
      }
      r.setMediaPackage(mp);
      final MetadataList metadataList = new MetadataList();
      final MediaPackageElementFlavor flavor = new MediaPackageElementFlavor("dublincore", "episode");
      final EventCatalogUIAdapter adapter = catalogUIAdapters.stream().filter(e -> e.getFlavor().equals(flavor)).findAny()
              .orElse(null);
      if (adapter == null) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
      final MetadataCollection collection = adapter.getRawFields();
      String seriesId = "";
      for (FileItemIterator iter = new ServletFileUpload().getItemIterator(request); iter.hasNext();) {
        FileItemStream item = iter.next();
        final String fieldName = item.getFieldName();
        if (fieldName.equals("hidden_series_name")) {
          seriesId = resolveSeriesName(Streams.asString(item.openStream()));
          replaceField(collection, DublinCore.PROPERTY_IS_PART_OF.getLocalName(), seriesId);
        } else if (fieldName.equals("isPartOf")) {
          final String fieldValue = Streams.asString(item.openStream());
          if (!fieldValue.isEmpty()) {
            seriesId = fieldValue;
            replaceField(collection, item.getFieldName(), fieldValue);
          }
        } else if (item.isFormField()) {
          final String fieldValue = Streams.asString(item.openStream());
          replaceField(collection, item.getFieldName(), fieldValue);
        } else {
          r.setMediaPackage(
                  ingestService.addTrack(item.openStream(), item.getName(), MediaPackageElements.PRESENTER_SOURCE, mp));
        }
      }
      final SimpleDateFormat sdf = MetadataField
              .getSimpleDateFormatter(collection.getOutputFields().get("startDate").getPattern().get());
      replaceField(collection, "startDate", sdf.format(new Date()));
      replaceField(collection, "duration", "6000");

      r.setAcl(new AccessControlList(new AccessControlEntry("ROLE_ADMIN", "write", true),
              new AccessControlEntry("ROLE_ADMIN", "read", true),
              new AccessControlEntry("ROLE_OAUTH_USER", "write", true),
              new AccessControlEntry("ROLE_OAUTH_USER", "read", true)));
      r.setProcessing(
              (JSONObject) new JSONParser().parse(
                      String.format("{\"workflow\":\"%s\",\"configuration\":%s}", workflow, workflowConfiguration)));
      r.setMetadataList(metadataList);
      metadataList.add(adapter, collection);

      JSONObject source = new JSONObject();
      source.put("type", "UPLOAD");
      r.setSource(source);
      indexService.createEvent(r);


      final String location = "/ltitools/upload/index.html?series=" + seriesId;
      return Response.ok().entity("Upload complete, <a href=\"" + location + "\">go back</a>").build();
    } catch (IllegalArgumentException | DateTimeParseException e) {
      logger.debug("Unable to create event", e);
      return RestUtil.R.badRequest(e.getMessage());
    } catch (SchedulerException e) {
      if (e.getCause() != null && e.getCause() instanceof NotFoundException
              || e.getCause() instanceof IllegalArgumentException) {
        logger.debug("Unable to create event", e);
        return RestUtil.R.badRequest(e.getCause().getMessage());
      } else {
        logger.error("Unable to create event", e);
        throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
      }
    } catch (Exception e) {
      logger.error("Unable to create event", e);
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }
  }

  private void replaceField(MetadataCollection collection, String fieldName, String fieldValue) {
    final MetadataField<?> field = collection.getOutputFields().get(fieldName);
    collection.removeField(field);
    collection.addField(MetadataField.copyMetadataFieldWithValue(field, fieldValue));
  }

  private String resolveSeriesName(String seriesName) throws SeriesException, UnauthorizedException {
    DublinCoreCatalogList result;
    result = seriesService.getSeries(new SeriesQuery().setSeriesTitle(seriesName));
    if (result.getTotalCount() == 0) {
      throw new IllegalArgumentException("series with given name doesn't exist");
    }
    if (result.getTotalCount() > 1) {
      throw new IllegalArgumentException("more than one series matches given series name");
    }
    DublinCoreCatalog seriesResult = result.getCatalogList().get(0);
    final List<DublinCoreValue> identifiers = seriesResult.get(DublinCore.PROPERTY_IDENTIFIER);
    if (identifiers.size() != 1) {
      throw new IllegalArgumentException("more than one identifier in dublin core catalog for series");
    }
    return identifiers.get(0).getValue();
  }

  @DELETE
  @Path("{eventId}")
  @RestQuery(name = "deleteevent", description = "Deletes an event.", returnDescription = "", pathParameters = {
          @RestParameter(name = "eventId", description = "The event id", isRequired = true, type = STRING) }, reponses = {
          @RestResponse(description = "The event has been deleted.", responseCode = HttpServletResponse.SC_NO_CONTENT),
          @RestResponse(description = "The specified event does not exist.", responseCode = HttpServletResponse.SC_NOT_FOUND) })
  public Response deleteEvent(@HeaderParam("Accept") String acceptHeader, @PathParam("eventId") String id)
          throws NotFoundException, UnauthorizedException {
    try {
      final Opt<Event> event = indexService.getEvent(id, abstractSearchIndex);
      if (event.isNone()) {
        return Response.serverError().entity("Event '" + id + "' not found").build();
      }
      final IndexService.EventRemovalResult eventRemovalResult = indexService.removeEvent(event.get(), () -> {
      }, retractWorkflowId);
      if (eventRemovalResult == IndexService.EventRemovalResult.GENERAL_FAILURE) {
        return Response.serverError().entity("Error removing event: " + eventRemovalResult).build();
      }
    } catch (WorkflowDatabaseException | SearchIndexException e) {
      return Response.serverError().entity("Error retrieving event").build();
    }

    return Response.noContent().build();
  }
}
