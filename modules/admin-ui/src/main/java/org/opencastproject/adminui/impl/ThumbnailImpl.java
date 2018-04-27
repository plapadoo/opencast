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

package org.opencastproject.adminui.impl;

import static org.opencastproject.mediapackage.MediaPackageElementFlavor.flavor;
import static org.opencastproject.mediapackage.MediaPackageElementFlavor.parseFlavor;
import static org.opencastproject.systems.OpencastConstants.WORKFLOW_PROPERTIES_NAMESPACE;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.assetmanager.api.Property;
import org.opencastproject.assetmanager.api.PropertyId;
import org.opencastproject.assetmanager.api.Value;
import org.opencastproject.assetmanager.api.query.AQueryBuilder;
import org.opencastproject.assetmanager.api.query.AResult;
import org.opencastproject.composer.api.ComposerService;
import org.opencastproject.composer.api.EncoderException;
import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobBarrier;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.attachment.AttachmentImpl;
import org.opencastproject.publication.api.ConfigurablePublicationService;
import org.opencastproject.publication.api.OaiPmhPublicationService;
import org.opencastproject.publication.api.PublicationException;
import org.opencastproject.security.urlsigning.exception.UrlSigningException;
import org.opencastproject.security.urlsigning.service.UrlSigningService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.util.MimeType;
import org.opencastproject.util.MimeTypes;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.UnknownFileTypeException;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.workflow.handler.distribution.InternalPublicationChannel;
import org.opencastproject.workspace.api.Workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ThumbnailImpl {
  /** Name of the thumbnail type workflow property */
  private static final String THUMBNAIL_PROPERTY_TYPE = "thumbnailType";
  /** Name of the thumbnail position workflow property */
  private static final String THUMBNAIL_PROPERTY_POSITION = "thumbnailPosition";
  /** Name of the thumbnail track workflow property */
  private static final String THUMBNAIL_PROPERTY_TRACK = "thumbnailTrack";

  public enum ThumbnailSource {
    DEFAULT(0),
    UPLOAD(1),
    SNAPSHOT(2);

    private long number;

    ThumbnailSource(final long number) {
      this.number = number;

    }

    public long getNumber() {
      return number;
    }

    public static ThumbnailSource byNumber(final long number) {
      return Arrays.stream(ThumbnailSource.values()).filter(v -> v.number == number).findFirst().orElse(DEFAULT);
    }
  }

  public static class Thumbnail {
    private final ThumbnailSource type;
    private final Double position;
    private final String track;
    private final URI url;

    public Thumbnail(final ThumbnailSource type, final Double position, final String track, final URI url) {
      this.type = type;
      this.position = position;
      this.track = track;
      this.url = url;
    }

    public ThumbnailSource getType() {
      return type;
    }

    public OptionalDouble getPosition() {
      if (position == null)
        return OptionalDouble.empty();
      return OptionalDouble.of(position);
    }

    public Optional<String> getTrack() {
      if (track == null)
        return Optional.empty();
      return Optional.of(track);
    }

    public URI getUrl() {
      return url;
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(ThumbnailImpl.class.getName());

  private final MediaPackageElementFlavor sourceFlavor;
  private final MediaPackageElementFlavor previewFlavor;
  private final MediaPackageElementFlavor publishFlavor;
  private final List<String> publishTags;
  private final Workspace workspace;
  private final ServiceRegistry serviceRegistry;
  private final OaiPmhPublicationService oaiPmhPublicationService;
  private final AssetManager assetManager;
  private final ConfigurablePublicationService configurablePublicationService;
  private final ComposerService composerService;
  private final String oaiPmhChannel;
  private final String encodingProfile;
  private final double defaultPosition;
  private final MediaPackageElementFlavor defaultTrackPrimary;
  private final MediaPackageElementFlavor defaultTrackSecondary;
  private String tempThumbnailFileName;
  private String tempThumbnailId;
  private URI tempThumbnail;
  private MimeType tempThumbnailMimeType;

  public ThumbnailImpl(final AdminUIConfiguration config, final Workspace workspace,
    final ServiceRegistry serviceRegistry, final OaiPmhPublicationService oaiPmhPublicationService,
    final ConfigurablePublicationService configurablePublicationService, final AssetManager assetManager,
    final ComposerService composerService) {
    this.sourceFlavor = flavor(config.getThumbnailSourceFlavorType(), config.getThumbnailSourceFlavorSubtype());
    this.previewFlavor = parseFlavor(config.getThumbnailPreviewFlavor());
    this.publishFlavor = parseFlavor(config.getThumbnailPublishFlavor());
    this.publishTags = Arrays.asList(config.getThumbnailPublishTags().split(","));
    this.oaiPmhChannel = config.getOaipmhChannel();
    this.encodingProfile = config.getThumbnailEncodingProfile();
    this.defaultPosition = config.getThumbnailDefaultPosition();
    this.defaultTrackPrimary = flavor(config.getThumbnailDefaultTrackPrimary(), config.getThumbnailSourceFlavorSubtype());
    this.defaultTrackSecondary = flavor(config.getThumbnailDefaultTrackSecondary(), config.getThumbnailSourceFlavorSubtype());
    this.workspace = workspace;
    this.serviceRegistry = serviceRegistry;
    this.oaiPmhPublicationService = oaiPmhPublicationService;
    this.assetManager = assetManager;
    this.composerService = composerService;
    this.configurablePublicationService = configurablePublicationService;
    this.tempThumbnail = null;
    this.tempThumbnailId = null;
    this.tempThumbnailMimeType = null;
    this.tempThumbnailFileName = null;
  }

  private Optional<Attachment> getThumbnailPreviewForMediaPackage(final MediaPackage mp) {
    final Optional<Publication> internalPublication = getPublication(mp, InternalPublicationChannel.CHANNEL_ID);
    if (!internalPublication.isPresent()) {
      throw new IllegalStateException("Expected internal publication, but found none for mp " + mp.getIdentifier());
    }
    return Arrays
      .stream(internalPublication.get().getAttachments())
      .filter(attachment -> previewFlavor.matches(attachment.getFlavor()))
      .findFirst();
  }

  public Optional<Thumbnail> getThumbnail(final MediaPackage mp, final UrlSigningService urlSigningService, final Long expireSeconds)
    throws UrlSigningException, URISyntaxException {
    final Optional<Attachment> optThumbnail = getThumbnailPreviewForMediaPackage(mp);
    if (!optThumbnail.isPresent())
      return Optional.empty();
    final Attachment thumbnail = optThumbnail.get();
    final URI url;
    if (urlSigningService.accepts(thumbnail.getURI().toString())) {
      url = new URI(urlSigningService.sign(optThumbnail.get().getURI().toString(), expireSeconds, null, null));
    } else {
      url = thumbnail.getURI();
    }

    final AQueryBuilder q = assetManager.createQuery();
    final AResult r = q.select(q.propertiesOf(WORKFLOW_PROPERTIES_NAMESPACE))
      .where(q.mediaPackageId(mp.getIdentifier().compact()).and(q.version().isLatest())).run();
    final List<Property> ps = r.getRecords().head2().getProperties().toList();
    final ThumbnailSource source = ps.stream()
      .filter(p -> ThumbnailImpl.THUMBNAIL_PROPERTY_TYPE.equals(p.getId().getName()))
      .map(e -> e.getValue().get(Value.STRING))
      .map(Long::parseLong)
      .map(ThumbnailSource::byNumber)
      .findAny()
      .orElse(ThumbnailSource.DEFAULT);
    final Double position = ps.stream()
      .filter(p -> ThumbnailImpl.THUMBNAIL_PROPERTY_POSITION.equals(p.getId().getName()))
      .map(e -> e.getValue().get(Value.STRING))
      .map(Double::parseDouble)
      .findAny().orElse(null);
    final String track = ps.stream()
      .filter(p -> ThumbnailImpl.THUMBNAIL_PROPERTY_TRACK.equals(p.getId().getName()))
      .map(e -> e.getValue().get(Value.STRING))
      .findAny().orElse(null);

    return Optional.of(new Thumbnail(source, position, track, url));
  }

  public MediaPackageElement upload(final MediaPackage mp, final InputStream inputStream, final String contentType)
    throws IOException, DistributionException, NotFoundException, MediaPackageException, PublicationException,
    EncoderException {
    createTempThumbnail(mp, inputStream, contentType);

    final ArrayList<URI> deletionUris = new ArrayList<>(0);
    try {
      // Archive uploaded thumbnail (and remove old one)
      archive(mp);

      final MediaPackageElementFlavor trackFlavor = getPrimaryOrSecondaryTrack(mp).getFlavor();

      final Tuple<URI, MediaPackageElement> internalPublicationResult = updateInternalPublication(mp, true);
      deletionUris.add(internalPublicationResult.getA());
      deletionUris.add(updateExternalPublication(mp, trackFlavor));
      deletionUris.add(updateOaiPmh(mp, trackFlavor));

      assetManager.takeSnapshot(mp);

      // Set workflow settings: type = UPLOAD
      assetManager.setProperty(Property
        .mk(PropertyId.mk(mp.getIdentifier().compact(), WORKFLOW_PROPERTIES_NAMESPACE, THUMBNAIL_PROPERTY_TYPE),
          org.opencastproject.assetmanager.api.Value.mk(Long.toString(ThumbnailSource.UPLOAD.getNumber()))));

      return internalPublicationResult.getB();
    } finally {
      inputStream.close();
      workspace.cleanup(mp.getIdentifier());
      for (final URI uri : deletionUris) {
        if (uri != null)
          workspace.delete(uri);
      }
    }
  }

  private Track getPrimaryOrSecondaryTrack(MediaPackage mp) throws MediaPackageException {

    final Optional<Track> track = Optional.ofNullable(
      Arrays.stream(mp.getTracks(defaultTrackPrimary)).findFirst()
        .orElse(Arrays.stream(mp.getTracks(defaultTrackSecondary)).findFirst()
          .orElse(null)));

    if (!track.isPresent()) {
      throw new MediaPackageException("Cannot find stream with primary or secondaŕy default flavor.");
    }
    return track.get();
  }

  private void archive(final MediaPackage mp) {
    final Attachment sourceAttachment = AttachmentImpl.fromURI(tempThumbnail);
    sourceAttachment.setIdentifier(tempThumbnailId);
    sourceAttachment.setFlavor(sourceFlavor);
    sourceAttachment.setMimeType(this.tempThumbnailMimeType);
    Arrays.stream(mp.getElementsByFlavor(sourceFlavor)).forEach(mp::remove);
    mp.add(sourceAttachment);
  }

  private Tuple<URI, MediaPackageElement> updateInternalPublication(final MediaPackage mp, final boolean downscale)
    throws DistributionException, NotFoundException, IOException, MediaPackageException, PublicationException,
    EncoderException {
    final Predicate<Attachment> priorFilter = attachment -> previewFlavor.matches(attachment.getFlavor());
    final String conversionProfile;
    if (downscale) {
       conversionProfile = "editor.thumbnail.preview.downscale";
    } else {
      conversionProfile = null;
    }
    return updatePublication(mp, InternalPublicationChannel.CHANNEL_ID, priorFilter, previewFlavor,
      Collections.emptyList(), conversionProfile);
  }

  private URI updateOaiPmh(final MediaPackage mp, final MediaPackageElementFlavor trackFlavor)
    throws NotFoundException, IOException, PublicationException, MediaPackageException {
    // Use OaiPmhPublicationService to re-publish thumbnail
    final Optional<Publication> oldOaiPmhPub = getPublication(mp,
      OaiPmhPublicationService.PUBLICATION_CHANNEL_PREFIX + this.oaiPmhChannel);
    if (!oldOaiPmhPub.isPresent()) {
      return null;
    }

    // We also have to update the external api publication in OAI-PMH...
    final Optional<Publication> externalPublicationOpt = getPublication(mp, "api");

    final Set<MediaPackageElement> publicationsToUpdate = new HashSet<>();
    final Set<MediaPackageElementFlavor> flavorsToRetract = new HashSet<>();
    externalPublicationOpt.ifPresent(publicationsToUpdate::add);
    externalPublicationOpt.map(MediaPackageElement::getFlavor).ifPresent(flavorsToRetract::add);
    final String publishThumbnailId = UUID.randomUUID().toString();
    final InputStream inputStream = tempInputStream();
    final URI publishThumbnailUri = workspace
      .put(mp.getIdentifier().compact(), publishThumbnailId, this.tempThumbnailFileName, inputStream);
    inputStream.close();
    final Attachment publishAttachment = AttachmentImpl.fromURI(publishThumbnailUri);
    publishAttachment.setIdentifier(UUID.randomUUID().toString());
    publishAttachment.setFlavor(publishFlavor.applyTo(trackFlavor));
    publishTags.forEach(publishAttachment::addTag);
    publishAttachment.setMimeType(this.tempThumbnailMimeType);
    publicationsToUpdate.add(publishAttachment);
    flavorsToRetract.add(publishFlavor);
    final long replaceStart = System.currentTimeMillis();
    final Job publishJob = oaiPmhPublicationService.replace(mp, oaiPmhChannel, publicationsToUpdate,
      Collections.emptySet(), flavorsToRetract, Collections.emptySet(), false);
    logger.info("Replace for OAIPMH took {}ms", (System.currentTimeMillis() - replaceStart));
    if (!waitForJob(this.serviceRegistry, publishJob).isSuccess()) {
      throw new PublicationException("Wait for OAIPMH publish job failed");
    }
    final Publication oaiPmhPub = (Publication) MediaPackageElementParser.getFromXml(publishJob.getPayload());
    mp.remove(oldOaiPmhPub.get());
    mp.add(oaiPmhPub);
    return publishThumbnailUri;
  }

  private Tuple<URI, MediaPackageElement> updatePublication(final MediaPackage mp, final String channelId,
    final Predicate<Attachment> priorFilter, final MediaPackageElementFlavor flavor, final Collection<String> tags, final String conversionProfile)
    throws DistributionException, NotFoundException, IOException, MediaPackageException, PublicationException,
    EncoderException {
    final Optional<Publication> pubOpt = getPublication(mp, channelId);
    if (!pubOpt.isPresent()) {
      return null;
    }
    final Publication pub = pubOpt.get();

    final String aid = UUID.randomUUID().toString();
    final InputStream inputStream = tempInputStream();
    final URI aUri = workspace.put(mp.getIdentifier().compact(), aid, tempThumbnailFileName, inputStream);
    inputStream.close();
    final Attachment a = AttachmentImpl.fromURI(aUri);
    a.setIdentifier(aid);
    a.setFlavor(flavor);
    tags.forEach(a::addTag);
    a.setMimeType(tempThumbnailMimeType);
    if (conversionProfile != null) {
      downscaleAttachment(conversionProfile, a);
    }

    final Collection<MediaPackageElement> addElements = Collections.singleton(a);
    final Collection<String> removeElements = Arrays.stream(pub.getAttachments()).filter(priorFilter)
      .map(MediaPackageElement::getIdentifier).collect(Collectors.toList());
    final Publication newPublication = replaceIgnoreExceptions(mp, channelId, addElements, removeElements);
    mp.remove(pub);
    mp.add(newPublication);
    //noinspection ConstantConditions
    final Attachment newElement = Arrays.stream(newPublication.getAttachments())
      .filter(att -> att.getIdentifier().equals(aid)).findAny().get();
    return Tuple.tuple(aUri, newElement);
  }

  private void downscaleAttachment(final String conversionProfile, final Attachment a)
    throws EncoderException, MediaPackageException, DistributionException {
    final Job conversionJob = composerService.convertImage(a,conversionProfile);
    if (!waitForJob(serviceRegistry, conversionJob).isSuccess()) {
      throw new DistributionException("Image downscaling did not work");
    }
    // What the composer returns is not our original attachment, modified, but a new one, basically containing just
    // a URI.
    final Attachment downscaled = (Attachment) MediaPackageElementParser.getFromXml(conversionJob.getPayload());
    a.setURI(downscaled.getURI());
  }

  private URI updateExternalPublication(final MediaPackage mp, final MediaPackageElementFlavor trackFlavor)
    throws IOException, NotFoundException, MediaPackageException, DistributionException, PublicationException,
    EncoderException {
    final Predicate<Attachment> flavorFilter = a -> a.getFlavor().matches(publishFlavor);
    final Predicate<Attachment> tagsFilter = a -> publishTags.stream()
      .allMatch(t -> Arrays.asList(a.getTags()).contains(t));
    final Predicate<Attachment> priorFilter = flavorFilter.and(tagsFilter);
    final Tuple<URI, MediaPackageElement> result = updatePublication(mp, "api", priorFilter,
      publishFlavor.applyTo(trackFlavor), publishTags, null);
    if (result == null)
      return null;
    return result.getA();
  }

  private InputStream tempInputStream() throws NotFoundException, IOException {
    return workspace.read(tempThumbnail);
  }

  private void createTempThumbnail(final MediaPackage mp, final InputStream inputStream, final String contentType)
    throws IOException {
    tempThumbnailMimeType = MimeTypes.parseMimeType(contentType);
    final String filename = "uploaded_thumbnail." + tempThumbnailMimeType.getSuffix().getOrElse("unknown");
    final String originalThumbnailId = UUID.randomUUID().toString();
    tempThumbnail = workspace.put(mp.getIdentifier().compact(), originalThumbnailId, filename, inputStream);
    tempThumbnailFileName = "uploaded_thumbnail." + tempThumbnailMimeType.getSuffix().getOrElse("unknown");
  }

  private Optional<Publication> getPublication(final MediaPackage mp, final String channelId) {
    return Arrays.stream(mp.getPublications()).filter(p -> p.getChannel().equalsIgnoreCase(channelId)).findAny();
  }

  private static JobBarrier.Result waitForJob(final ServiceRegistry reg, final Job job) {
    final Job.Status status = job.getStatus();
    // only create a barrier if the job is not done yet
    switch (status) {
      case CANCELED:
      case DELETED:
      case FAILED:
      case FINISHED:
        return new JobBarrier.Result(Collections.singletonMap(job, status));
      default:
        return waitForJobs(reg, Collections.singleton(job));
    }
  }

  private static JobBarrier.Result waitForJobs(final ServiceRegistry reg, final Collection<Job> jobs) {
    final JobBarrier barrier = new JobBarrier(null, reg, 100, jobs.toArray(new Job[jobs.size()]));
    return barrier.waitForJobs();
  }

  private Publication replaceIgnoreExceptions(final MediaPackage mp, final String channelId,
    final Collection<? extends MediaPackageElement> addElementIds, final Collection<String> retractElementIds)
    throws DistributionException, MediaPackageException, PublicationException {

    final Job j = this.configurablePublicationService.replace(mp, channelId, addElementIds, retractElementIds);

    if (!waitForJob(serviceRegistry, j).isSuccess()) {
      throw new DistributionException("At least one of the retraction jobs did not complete successfully");
    }

    return (Publication) MediaPackageElementParser.getFromXml(j.getPayload());
  }

  private MediaPackageElement chooseThumbnail(final MediaPackage mp, final Track track, final double position)
    throws PublicationException, MediaPackageException, EncoderException, IOException, NotFoundException,
    DistributionException, UnknownFileTypeException {
    final Job job = composerService.image(track, encodingProfile, position);
    if (!waitForJob(serviceRegistry, job).isSuccess()) {
      throw new EncoderException("Could not create thumbnail.");
    }

    tempThumbnail = MediaPackageElementParser.getArrayFromXml(job.getPayload()).get(0).getURI();
    tempThumbnailMimeType = MimeTypes.fromURI(tempThumbnail);
    tempThumbnailFileName = tempThumbnail.getPath().substring(tempThumbnail.getPath().lastIndexOf('/') + 1);

    final ArrayList<URI> deletionUris = new ArrayList<>(0);
    try {

      // Remove any uploaded thumbnails
      Arrays.stream(mp.getElementsByFlavor(sourceFlavor)).forEach(mp::remove);

      final Tuple<URI, MediaPackageElement> internalPublicationResult = updateInternalPublication(mp, false);
      deletionUris.add(internalPublicationResult.getA());
      deletionUris.add(updateExternalPublication(mp, track.getFlavor()));
      deletionUris.add(updateOaiPmh(mp, track.getFlavor()));

      assetManager.takeSnapshot(mp);

      return internalPublicationResult.getB();
    } finally {
      workspace.cleanup(mp.getIdentifier());
      for (final URI uri : deletionUris) {
        if (uri != null)
          workspace.delete(uri);
      }
    }
  }

  public MediaPackageElement chooseDefaultThumbnail(final MediaPackage mp)
    throws PublicationException, MediaPackageException, EncoderException, IOException, NotFoundException,
    DistributionException, UnknownFileTypeException {

    final MediaPackageElement result = chooseThumbnail(mp, getPrimaryOrSecondaryTrack(mp), defaultPosition);

    // Set workflow settings: type = DEFAULT
    assetManager.setProperty(Property
      .mk(PropertyId.mk(mp.getIdentifier().compact(), WORKFLOW_PROPERTIES_NAMESPACE, THUMBNAIL_PROPERTY_TYPE),
        org.opencastproject.assetmanager.api.Value.mk(Long.toString(ThumbnailSource.DEFAULT.getNumber()))));

    return result;
  }

  public MediaPackageElement chooseThumbnail(final MediaPackage mp, final String trackFlavorType, final double position)
    throws PublicationException, MediaPackageException, EncoderException, IOException, NotFoundException,
    DistributionException, UnknownFileTypeException {

    final MediaPackageElementFlavor trackFlavor = flavor(trackFlavorType, sourceFlavor.getSubtype());
    final Optional<Track> track = Arrays.stream(mp.getTracks(trackFlavor)).findFirst();

    if (!track.isPresent()) {
      throw new MediaPackageException("Cannot find stream with flavor " + trackFlavor + " to extract thumbnail.");
    }

    final MediaPackageElement result =  chooseThumbnail(mp, track.get(), position);

    // Set workflow settings: type = SNAPSHOT, position, track
    assetManager.setProperty(Property
      .mk(PropertyId.mk(mp.getIdentifier().compact(), WORKFLOW_PROPERTIES_NAMESPACE, THUMBNAIL_PROPERTY_TYPE),
        org.opencastproject.assetmanager.api.Value.mk(Long.toString(ThumbnailSource.SNAPSHOT.getNumber()))));
    // We switch from double to string here because the AssetManager cannot store doubles, and we need a double value
    // in the workflow (properties)
    assetManager.setProperty(Property
      .mk(PropertyId.mk(mp.getIdentifier().compact(), WORKFLOW_PROPERTIES_NAMESPACE, THUMBNAIL_PROPERTY_POSITION),
        org.opencastproject.assetmanager.api.Value.mk(Double.toString(position))));
    assetManager.setProperty(Property
      .mk(PropertyId.mk(mp.getIdentifier().compact(), WORKFLOW_PROPERTIES_NAMESPACE, THUMBNAIL_PROPERTY_TRACK),
        org.opencastproject.assetmanager.api.Value.mk(trackFlavor.getType())));

    return result;
  }
}
