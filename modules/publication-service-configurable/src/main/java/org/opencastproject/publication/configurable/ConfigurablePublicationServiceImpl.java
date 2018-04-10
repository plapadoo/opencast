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
package org.opencastproject.publication.configurable;

import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.mediapackage.PublicationImpl;
import org.opencastproject.publication.api.ConfigurablePublicationService;
import org.opencastproject.publication.api.PublicationException;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.JobUtil;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigurablePublicationServiceImpl extends AbstractJobProducer implements ConfigurablePublicationService {
  private static final Logger logger = LoggerFactory.getLogger(ConfigurablePublicationServiceImpl.class);
  private static final String SEPARATOR = ";;";
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile(SEPARATOR);

  @Override
  public void activate(ComponentContext cc) {
    super.activate(cc);
  }

  public ConfigurablePublicationServiceImpl() {
    super(JOB_TYPE);
  }

  @Override
  public String getJobType() {
    return super.getJobType();
  }

  public enum Operation {
    Replace
  }

  private DistributionService distributionService;

  private SecurityService securityService;

  private UserDirectoryService userDirectoryService;

  private OrganizationDirectoryService organizationDirectoryService;

  private ServiceRegistry serviceRegistry;

  public void setDistributionService(final DistributionService distributionService) {
    this.distributionService = distributionService;
  }

  public void setServiceRegistry(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @Override
  protected ServiceRegistry getServiceRegistry() {
    return this.serviceRegistry;
  }

  @Override
  protected SecurityService getSecurityService() {
    return this.securityService;
  }

  public void setSecurityService(final SecurityService securityService) {
    this.securityService = securityService;
  }

  public void setUserDirectoryService(final UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  public void setOrganizationDirectoryService(final OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }

  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return this.userDirectoryService;
  }

  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return this.organizationDirectoryService;
  }

  private static String flattenStrings(final Collection<String> s) {
    return s.stream().collect(Collectors.joining(SEPARATOR));
  }

  private static Collection<String> unflattenStrings(final String s) {
    return SEPARATOR_PATTERN.splitAsStream(s).collect(Collectors.toSet());
  }

  @Override
  public Job replace(final MediaPackage mediaPackage, final String channelId,
          final Collection<? extends MediaPackageElement> addElements, final Collection<String> retractElementIds)
          throws PublicationException, MediaPackageException {
    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Replace.toString(),
              Arrays.asList(MediaPackageParser.getAsXml(mediaPackage), channelId,
                      MediaPackageElementParser.getArrayAsXml(addElements), flattenStrings(retractElementIds)));
    } catch (final ServiceRegistryException e) {
      throw new PublicationException("Unable to create job", e);
    }
  }

  @Override
  protected String process(final Job job) throws Exception {
    final List<String> args = job.getArguments();
    final MediaPackage mediaPackage = MediaPackageParser.getFromXml(args.get(0));
    final String channelId = args.get(1);
    final Collection<? extends MediaPackageElement> addElementIds = MediaPackageElementParser
            .getArrayFromXml(args.get(2));
    final Collection<String> retractElementIds = unflattenStrings(args.get(3));

    Publication result = null;
    switch (Operation.valueOf(job.getOperation())) {
      case Replace:
        result = doReplace(mediaPackage, channelId, addElementIds, retractElementIds);
        break;
      default:
        break;
    }
    if (result != null)
      return MediaPackageElementParser.getAsXml(result);
    return null;
  }

  private void distributeMany(final MediaPackage mp, final String channelId,
          final Collection<? extends MediaPackageElement> elements)
          throws DistributionException, MediaPackageException {
    // Add all the elements top-level so the distribution service knows what to do
    elements.forEach(mp::add);

    final Optional<Publication> publicationOpt = Arrays.stream(mp.getPublications())
            .filter(p -> p.getChannel().equalsIgnoreCase(channelId)).findAny();

    if (!publicationOpt.isPresent())
      return;

    final Publication publication = publicationOpt.get();

    try {
      final List<Job> jobs = new ArrayList<>();
      // Then distribute them all in parallel, collecting the jobs
      for (final MediaPackageElement mpe : elements) {
        jobs.add(distributionService.distribute(channelId, mp, mpe.getIdentifier()));
      }

      // Then, wait for the jobs
      for (final Job j : jobs) {
        if (!JobUtil.waitForJob(serviceRegistry, j).isSuccess())
          throw new DistributionException("At least one of the publication jobs did not complete successfully");
        final MediaPackageElement jobResult = MediaPackageElementParser.getFromXml(j.getPayload());
        PublicationImpl.addElementToPublication(publication, jobResult);
      }
    } finally {
      // Remove our changes
      elements.stream().map(MediaPackageElement::getIdentifier).forEach(mp::removeElementById);
    }
  }

  private List<Job> retractMany(final MediaPackage mp, final String channelId, final Collection<String> elementIds)
          throws DistributionException {
    final List<Job> result = new ArrayList<>();
    for (final String elementId : elementIds) {
      result.add(distributionService.retract(channelId, mp, elementId));
    }
    return result;
  }

  private Publication doReplace(final MediaPackage mp, final String channelId,
          final Collection<? extends MediaPackageElement> addElementIds, final Collection<String> retractElementIds)
          throws DistributionException, MediaPackageException {
    final List<Job> retractions = retractMany(mp, channelId, retractElementIds);
    // Retract old elements
    if (!JobUtil.waitForJobs(serviceRegistry, retractions).isSuccess()) {
      throw new DistributionException("At least one of the retraction jobs did not complete successfully");
    }

    final Optional<Publication> priorPublication = getPublication(mp, channelId);

    final Publication publication;
    if (priorPublication.isPresent()) {
      publication = priorPublication.get();
    } else {
      final String publicationUUID = UUID.randomUUID().toString();
      publication = PublicationImpl.publication(publicationUUID, channelId, null, null);
      mp.add(publication);
    }

    retractElementIds.forEach(publication::removeAttachmentById);

    distributeMany(mp, channelId, addElementIds);

    return publication;
  }

  private Optional<Publication> getPublication(final MediaPackage mp, final String channelId) {
    return Arrays.stream(mp.getPublications()).filter(p -> p.getChannel().equalsIgnoreCase(channelId)).findAny();
  }
}
