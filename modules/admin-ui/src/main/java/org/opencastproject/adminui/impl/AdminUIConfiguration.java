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

import org.opencastproject.mediapackage.MediaPackageElementFlavor;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;

public class AdminUIConfiguration implements ManagedService {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(AdminUIConfiguration.class);

  public static final String OPT_PREVIEW_SUBTYPE = "preview.subtype";
  public static final String OPT_WAVEFORM_SUBTYPE = "waveform.subtype";
  public static final String OPT_SMIL_CATALOG_FLAVOR = "smil.catalog.flavor";
  public static final String OPT_SMIL_CATALOG_TAGS = "smil.catalog.tags";
  public static final String OPT_SMIL_SILENCE_FLAVOR = "smil.silence.flavor";
  public static final String OPT_SOURCE_TRACK_LEFT_FLAVOR = "sourcetrack.left.flavor";
  public static final String OPT_SOURCE_TRACK_RIGHT_FLAVOR = "sourcetrack.right.flavor";
  public static final String OPT_PREVIEW_AUDIO_SUBTYPE = "preview.audio.subtype";
  public static final String OPT_PREVIEW_VIDEO_SUBTYPE = "preview.video.subtype";

  private static final String DEFAULT_PREVIEW_SUBTYPE = "preview";
  private static final String DEFAULT_WAVEFORM_SUBTYPE = "waveform";
  private static final String DEFAULT_SMIL_CATALOG_FLAVOR = "smil/cutting";
  private static final String DEFAULT_SMIL_CATALOG_TAGS = "archive";
  private static final String DEFAULT_SMIL_SILENCE_FLAVOR = "*/silence";
  private static final String DEFAULT_PREVIEW_VIDEO_SUBTYPE = "video+preview";
  private static final String DEFAULT_PREVIEW_AUDIO_SUBTYPE = "audio+preview";
  private static final String DEFAULT_SOURCE_TRACK_LEFT_FLAVOR = "presenter/source";
  private static final String DEFAULT_SOURCE_TRACK_RIGHT_FLAVOR = "presentation/source";

  private String previewSubtype = DEFAULT_PREVIEW_SUBTYPE;
  private String waveformSubtype = DEFAULT_WAVEFORM_SUBTYPE;
  private Set<String> smilCatalogTagSet = new HashSet<>();
  private MediaPackageElementFlavor smilCatalogFlavor = MediaPackageElementFlavor.parseFlavor(DEFAULT_SMIL_CATALOG_FLAVOR);
  private MediaPackageElementFlavor smilSilenceFlavor = MediaPackageElementFlavor.parseFlavor(DEFAULT_SMIL_SILENCE_FLAVOR);
  private String previewVideoSubtype = DEFAULT_PREVIEW_VIDEO_SUBTYPE;
  private String previewAudioSubtype = DEFAULT_PREVIEW_AUDIO_SUBTYPE;
  private MediaPackageElementFlavor sourceTrackLeftFlavor = MediaPackageElementFlavor.parseFlavor(
    DEFAULT_SOURCE_TRACK_LEFT_FLAVOR);
  private MediaPackageElementFlavor sourceTrackRightFlavor = MediaPackageElementFlavor.parseFlavor(
    DEFAULT_SOURCE_TRACK_RIGHT_FLAVOR);

  public String getPreviewSubtype() {
    return previewSubtype;
  }

  public String getWaveformSubtype() {
    return waveformSubtype;
  }

  public MediaPackageElementFlavor getSmilCatalogFlavor() {
    return smilCatalogFlavor;
  }

  public Set<String> getSmilCatalogTags() {
    return smilCatalogTagSet;
  }

  public MediaPackageElementFlavor getSmilSilenceFlavor() {
    return smilSilenceFlavor;
  }

  public String getPreviewVideoSubtype() {
    return previewVideoSubtype;
  }

  public String getPreviewAudioSubtype() {
    return previewAudioSubtype;
  }

  public MediaPackageElementFlavor getSourceTrackLeftFlavor() {
    return sourceTrackLeftFlavor;
  }

  public MediaPackageElementFlavor getSourceTrackRightFlavor() {
    return sourceTrackRightFlavor;
  }

  @Override
  public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
    if (properties == null)
      return;

    // Preview subtype
    previewSubtype = StringUtils.defaultString((String) properties.get(OPT_PREVIEW_SUBTYPE), DEFAULT_PREVIEW_SUBTYPE);
    logger.debug("Preview subtype configuration set to '{}'", previewSubtype);

    // Waveform subtype
    waveformSubtype = StringUtils.defaultString((String) properties.get(OPT_WAVEFORM_SUBTYPE), DEFAULT_WAVEFORM_SUBTYPE);
    logger.debug("Waveform subtype configuration set to '{}'", waveformSubtype);

    // SMIL catalog flavor
    smilCatalogFlavor = MediaPackageElementFlavor.parseFlavor(
      StringUtils.defaultString((String) properties.get(OPT_SMIL_CATALOG_FLAVOR), DEFAULT_SMIL_CATALOG_FLAVOR));
    logger.debug("Smil catalog flavor configuration set to '{}'", smilCatalogFlavor);

    // SMIL catalog tags
    String tags = StringUtils.defaultString((String) properties.get(OPT_SMIL_CATALOG_TAGS), DEFAULT_SMIL_CATALOG_TAGS);
    String[] smilCatalogTags = StringUtils.split(tags, ",");
    smilCatalogTagSet.clear();
    if (smilCatalogTags != null) {
      smilCatalogTagSet.addAll(Arrays.asList(smilCatalogTags));
    }
    logger.debug("Smil catalog target tags configuration set to '{}'", smilCatalogTagSet);

    // SMIL silence flavor
    smilSilenceFlavor = MediaPackageElementFlavor.parseFlavor(
      StringUtils.defaultString((String) properties.get(OPT_SMIL_SILENCE_FLAVOR), DEFAULT_SMIL_SILENCE_FLAVOR));
    logger.debug("Smil silence flavor configuration set to '{}'", smilSilenceFlavor);

    // Preview Video subtype
    previewVideoSubtype = StringUtils.defaultString((String) properties.get(OPT_PREVIEW_VIDEO_SUBTYPE),
      DEFAULT_PREVIEW_VIDEO_SUBTYPE);
    logger.debug("Preview video subtype set to '{}'", previewVideoSubtype);

    // Preview Audio subtype
    previewAudioSubtype = StringUtils.defaultString((String) properties.get(OPT_PREVIEW_AUDIO_SUBTYPE),
      DEFAULT_PREVIEW_AUDIO_SUBTYPE);
    logger.debug("Preview audio subtype set to '{}'", previewAudioSubtype);

    // Source track left flavor
    sourceTrackLeftFlavor = MediaPackageElementFlavor.parseFlavor(StringUtils.defaultString(
      (String) properties.get(OPT_SOURCE_TRACK_LEFT_FLAVOR), DEFAULT_SOURCE_TRACK_LEFT_FLAVOR));
    logger.debug("Source track left flavor set to '{}'", sourceTrackLeftFlavor);

    // Source track right flavor
    sourceTrackRightFlavor = MediaPackageElementFlavor.parseFlavor(StringUtils.defaultString(
      (String) properties.get(OPT_SOURCE_TRACK_RIGHT_FLAVOR), DEFAULT_SOURCE_TRACK_RIGHT_FLAVOR));
    logger.debug("Source track right flavor set to '{}'", sourceTrackRightFlavor);

  }
}
