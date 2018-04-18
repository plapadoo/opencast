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

package org.opencastproject.workflow.handler.composer;

import org.opencastproject.composer.api.ComposerService;
import org.opencastproject.composer.api.EncoderException;
import org.opencastproject.composer.api.EncodingProfile;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationTagUtil;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SelectTracksWorkflowOperationHandler extends AbstractWorkflowOperationHandler {
  private static final Logger logger = LoggerFactory.getLogger(SelectTracksWorkflowOperationHandler.class);

  /** Name of the 'encode to video only work copy' encoding profile */
  private static final String PREPARE_VIDEO_ONLY_PROFILE = "video-only.work";

  /** Name of the muxing encoding profile */
  private static final String MUX_AV_PROFILE = "mux-av.work";

  /** The composer service */
  private ComposerService composerService = null;

  /** The local workspace */
  private Workspace workspace = null;

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  static {
    CONFIG_OPTIONS = new TreeMap<>();
    CONFIG_OPTIONS.put("source-flavor", "The \"flavor\" of the track to use as a video source input");
    CONFIG_OPTIONS.put("target-flavor", "The flavor to apply to the encoded file");
    CONFIG_OPTIONS.put("target-tags", "The tags to apply to the encoded file");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#getConfigurationOptions()
   */
  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param composerService
   *          the local composer service
   */
  protected void setComposerService(final ComposerService composerService) {
    this.composerService = composerService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the local workspace service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param workspace
   *          an instance of the workspace
   */
  public void setWorkspace(final Workspace workspace) {
    this.workspace = workspace;
  }

  private EncodingProfile getProfile(final String identifier) {
    final EncodingProfile profile = this.composerService.getProfile(identifier);
    if (profile == null)
      throw new IllegalStateException(String.format("couldn't find encoding profile \"%s\"", identifier));
    return profile;
  }

  private static Optional<String> getConfiguration(final WorkflowInstance instance, final String key) {
    return Optional.ofNullable(instance.getCurrentOperation().getConfiguration(key)).map(StringUtils::trimToNull);
  }

  private enum SubTrack {
    AUDIO, VIDEO
  }

  private static final class AugmentedTrack {
    private Track track;
    private final boolean hideAudio;
    private final boolean hideVideo;

    private AugmentedTrack(final Track track, final boolean hideAudio, final boolean hideVideo) {
      this.track = track;
      this.hideAudio = hideAudio;
      this.hideVideo = hideVideo;
    }

    boolean has(final SubTrack t) {
      if (t == SubTrack.AUDIO) {
        return hasAudio();
      } else {
        return hasVideo();
      }
    }

    void resetTrack(final Track t) {
      track = t;
    }

    boolean hide(final SubTrack t) {
      if (t == SubTrack.AUDIO) {
        return hideAudio;
      } else {
        return hideVideo;
      }
    }

    boolean hasAudio() {
      return track.hasAudio();
    }

    boolean hasVideo() {
      return track.hasVideo();
    }

    void setFlavorSubtype(final String subtype) {
      track.setFlavor(new MediaPackageElementFlavor(track.getFlavor().getType(), subtype));
    }
  }

  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, final JobContext context)
          throws WorkflowOperationException {
    try {
      return doStart(workflowInstance);
    } catch (final EncoderException | MediaPackageException | IOException | NotFoundException e) {
      throw new WorkflowOperationException(e);
    }
  }

  private WorkflowOperationResult doStart(final WorkflowInstance workflowInstance)
          throws WorkflowOperationException, EncoderException, MediaPackageException, NotFoundException, IOException {
    final MediaPackage mediaPackage = workflowInstance.getMediaPackage();

    final MediaPackageElementFlavor sourceFlavor = getConfiguration(workflowInstance, "source-flavor")
            .map(MediaPackageElementFlavor::parseFlavor)
            .orElseThrow(() -> new IllegalStateException("Source flavor must be specified"));

    final Track[] tracks = mediaPackage.getTracks(sourceFlavor);

    if (tracks.length == 0) {
      logger.info("No audio/video tracks with flavor '{}' found to prepare", sourceFlavor);
      return createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE);
    }

    final List<AugmentedTrack> augmentedTracks = createAugmentedTracks(tracks, workflowInstance);

    long queueTime = 0L;
    // First case: We have only tracks with non-hidden video streams. So we keep them all and possibly cut away audio.
    if (allNonHidden(augmentedTracks, SubTrack.VIDEO)) {
      for (final AugmentedTrack t : augmentedTracks) {
        if (t.hasAudio() && t.hideAudio) {
          // The flavor gets "nulled" in the process. Reverse that so we can treat all tracks equally.
          final MediaPackageElementFlavor previousFlavor = t.track.getFlavor();
          final TrackJobResult trackJobResult = hideAudio(t.track, mediaPackage);
          trackJobResult.track.setFlavor(previousFlavor);
          t.resetTrack(trackJobResult.track);
          queueTime += trackJobResult.waitTime;
        } else {
          // Even if we don't modify the track, we clone and re-add it to the MP (since it will be a new track with a
          // different flavor)
          final Track clonedTrack = (Track) t.track.clone();
          clonedTrack.setIdentifier(null);
          mediaPackage.add(clonedTrack);
          t.resetTrack(clonedTrack);
        }
      }
    } else {
      // Otherwise, we have just one video track that's not hidden (because hopefully, the UI prevented all other
      // cases). We keep that, and mux in the audio from the other track.
      final AugmentedTrack nonHiddenVideo = findNonHidden(augmentedTracks, SubTrack.VIDEO)
              .orElseThrow(() -> new IllegalStateException("couldn't find a stream with non-hidden video"));
      // Implicit here is the assumption that there's just _one_ other audio stream. It's written so that
      // we can loosen this assumption later on.
      final Optional<AugmentedTrack> nonHiddenAudio = findNonHidden(augmentedTracks, SubTrack.AUDIO);

      // If there's just one non-hidden video stream, and that one has hidden audio, we have to cut that away, too.
      if (nonHiddenVideo.hasAudio() && nonHiddenVideo.hideAudio && (!nonHiddenAudio.isPresent()
              || nonHiddenAudio.get() == nonHiddenVideo)) {
        final TrackJobResult jobResult = hideAudio(nonHiddenVideo.track, mediaPackage);
        nonHiddenVideo.track = jobResult.track;
        queueTime += jobResult.waitTime;
      } else if (!nonHiddenAudio.isPresent() || nonHiddenAudio.get() == nonHiddenVideo) {
        // It could be the case that the non-hidden video stream is also the non-hidden audio stream. In that
        // case, we don't have to mux. But have to clone it.
        final Track clonedTrack = (Track) nonHiddenVideo.track.clone();
        clonedTrack.setIdentifier(null);
        mediaPackage.add(clonedTrack);
        nonHiddenVideo.resetTrack(clonedTrack);
      } else {
        // Otherwise, we mux!
        final TrackJobResult jobResult = mux(nonHiddenVideo.track, nonHiddenAudio.get().track, mediaPackage);
        nonHiddenVideo.track = jobResult.track;
        queueTime += jobResult.waitTime;
      }
      // ...and then throw away everything else.
      augmentedTracks.removeIf(t -> t.track != nonHiddenVideo.track);
    }

    final MediaPackageElementFlavor targetTrackFlavor = MediaPackageElementFlavor.parseFlavor(StringUtils.trimToNull(
            getConfiguration(workflowInstance, "target-flavor")
                    .orElseThrow(() -> new IllegalStateException("Target flavor not specified"))));

    // Update Flavor
    augmentedTracks.forEach(t -> t.setFlavorSubtype(targetTrackFlavor.getSubtype()));

    // Update Tags here
    getConfiguration(workflowInstance, "target-tags").ifPresent(tags -> {
      final WorkflowOperationTagUtil.TagDiff tagDiff = WorkflowOperationTagUtil.createTagDiff(tags);
      augmentedTracks.forEach(t -> WorkflowOperationTagUtil.applyTagDiff(tagDiff, t.track));
    });

    return createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE, queueTime);
  }

  private TrackJobResult mux(final Track videoTrack, final Track audioTrack, final MediaPackage mediaPackage)
          throws MediaPackageException, EncoderException, WorkflowOperationException, NotFoundException, IOException {
    // Find the encoding profile
    final EncodingProfile profile = getProfile(MUX_AV_PROFILE);

    final Job job = composerService.mux(videoTrack, audioTrack, profile.getIdentifier());
    if (!waitForStatus(job).isSuccess()) {
      throw new WorkflowOperationException(
              String.format("Muxing video track %s and audio track %s failed", videoTrack, audioTrack));
    }
    final MediaPackageElementFlavor previousFlavor = videoTrack.getFlavor();
    final TrackJobResult trackJobResult = processJob(videoTrack, mediaPackage, job);
    trackJobResult.track.setFlavor(previousFlavor);
    return trackJobResult;
  }

  private static final class TrackJobResult {
    private final Track track;
    private final long waitTime;

    private TrackJobResult(final Track track, final long waitTime) {
      this.track = track;
      this.waitTime = waitTime;
    }
  }

  private TrackJobResult hideAudio(final Track track, final MediaPackage mediaPackage)
          throws MediaPackageException, EncoderException, WorkflowOperationException, NotFoundException, IOException {
    // Find the encoding profile
    final EncodingProfile profile = getProfile(PREPARE_VIDEO_ONLY_PROFILE);
    logger.info("Encoding video only track {} to work version", track);
    final Job job = composerService.encode(track, profile.getIdentifier());
    if (!waitForStatus(job).isSuccess()) {
      throw new WorkflowOperationException(String.format("Rewriting container for video track %s failed", track));
    }
    final MediaPackageElementFlavor previousFlavor = track.getFlavor();
    final TrackJobResult trackJobResult = processJob(track, mediaPackage, job);
    trackJobResult.track.setFlavor(previousFlavor);
    return trackJobResult;
  }

  private TrackJobResult processJob(final Track track, final MediaPackage mediaPackage, final Job job)
          throws MediaPackageException, NotFoundException, IOException {
    final Track composedTrack = (Track) MediaPackageElementParser.getFromXml(job.getPayload());
    mediaPackage.add(composedTrack);
    final String fileName = getFileNameFromElements(track, composedTrack);

    // Note that the composed track must have an ID before being moved to the mediapackage in the working file
    // repository. This ID is generated when the track is added to the mediapackage. So the track must be added
    // to the mediapackage before attempting to move the file.
    composedTrack.setURI(workspace
            .moveTo(composedTrack.getURI(), mediaPackage.getIdentifier().toString(), composedTrack.getIdentifier(),
                    fileName));
    return new TrackJobResult(composedTrack, job.getQueueTime());
  }

  private Optional<AugmentedTrack> findNonHidden(final Collection<AugmentedTrack> augmentedTracks, final SubTrack st) {
    return augmentedTracks.stream().filter(t -> t.has(st) && !t.hide(st)).findAny();
  }

  private boolean allNonHidden(final Collection<AugmentedTrack> augmentedTracks,
          @SuppressWarnings("SameParameterValue") final SubTrack st) {
    return augmentedTracks.stream().noneMatch(t -> !t.has(st) || t.hide(st));
  }

  private static String constructHideProperty(final String s, final SubTrack st) {
    return "hide_" + s + "_" + st.toString().toLowerCase();
  }

  private boolean trackHidden(final WorkflowInstance instance, final String subtype, final SubTrack st) {
    final String hideProperty = instance.getConfiguration(constructHideProperty(subtype, st));
    return Boolean.parseBoolean(hideProperty);
  }

  private List<AugmentedTrack> createAugmentedTracks(final Track[] tracks, final WorkflowInstance instance) {
    return Arrays.stream(tracks)
                 .map(t -> {
                   final boolean hideAudio = trackHidden(instance, t.getFlavor().getType(), SubTrack.AUDIO);
                   final boolean hideVideo = trackHidden(instance, t.getFlavor().getType(), SubTrack.VIDEO);
                   return new AugmentedTrack(t, hideAudio, hideVideo);
                 })
                 .collect(Collectors.toList());
  }
}
