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
  public static final String OPT_THUMBNAIL_PUBLISH_FLAVOR = "thumbnail.publish.flavor";
  public static final String OPT_THUMBNAIL_PREVIEW_FLAVOR = "thumbnail.preview.flavor";
  public static final String OPT_THUMBNAIL_SOURCE_FLAVOR_TYPE = "thumbnail.source.flavor.type";
  public static final String OPT_THUMBNAIL_SOURCE_FLAVOR_SUBTYPE = "thumbnail.source.flavor.subtype";
  public static final String OPT_THUMBNAIL_PUBLISH_TAGS = "thumbnail.publish.tags";
  public static final String OPT_THUMBNAIL_ENCODING_PROFILE = "thumbnail.encoding.profile";
  public static final String OPT_THUMBNAIL_DEFAULT_POSITION = "thumbnail.default.position";
  public static final String OPT_THUMBNAIL_DEFAULT_TRACK_PRIMARY = "thumbnail.default.track.primary";
  public static final String OPT_THUMBNAIL_DEFAULT_TRACK_SECONDARY = "thumbnail.default.track.secondary";

  public static final String OPT_SMIL_CATALOG_FLAVOR = "smil.catalog.flavor";
  public static final String OPT_SMIL_CATALOG_TAGS = "smil.catalog.tags";
  public static final String OPT_SMIL_SILENCE_FLAVOR = "smil.silence.flavor";
  public static final String OPT_OAIPMH_CHANNEL = "oaipmh.channel";

  private String previewSubtype = "preview";
  private String waveformSubtype = "waveform";
  private String thumbnailPublishFlavor = "*/search+preview";
  private String thumbnailPreviewFlavor = "thumbnail/preview";
  private String thumbnailSourceFlavorType = "thumbnail";
  private String thumbnailSourceFlavorSubtype = "source";
  private String thumbnailPublishTags = "engage-download";
  private String thumbnailEncodingProfile = "search-cover.http";
  private Double thumbnailDefaultPosition = 1.0;
  private String thumbnailDefaultTrackPrimary = "presenter/source";
  private String thumbnailDefaultTrackSecondary = "presentation/source";
  private String oaipmhChannel = "default";
  private Set<String> smilCatalogTagSet = new HashSet<String>();
  private MediaPackageElementFlavor smilCatalogFlavor = new MediaPackageElementFlavor("smil", "cutting");
  private MediaPackageElementFlavor smilSilenceFlavor = new MediaPackageElementFlavor("*", "silence");

  public String getPreviewSubtype() {
    return previewSubtype;
  }

  public String getWaveformSubtype() {
    return waveformSubtype;
  }

  public String getThumbnailPublishFlavor() {
    return thumbnailPublishFlavor;
  }

  public String getThumbnailPreviewFlavor() {
    return thumbnailPreviewFlavor;
  }

  public String getThumbnailSourceFlavorType() {
    return thumbnailSourceFlavorType;
  }

  public String getThumbnailSourceFlavorSubtype() {
    return thumbnailSourceFlavorSubtype;
  }

  public String getThumbnailPublishTags() {
    return thumbnailPublishTags;
  }

  public String getThumbnailEncodingProfile() {
    return thumbnailEncodingProfile;
  }

  public Double getThumbnailDefaultPosition() {
    return thumbnailDefaultPosition;
  }

  public String getThumbnailDefaultTrackPrimary() {
    return thumbnailDefaultTrackPrimary;
  }

  public String getThumbnailDefaultTrackSecondary() {
    return thumbnailDefaultTrackSecondary;
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


  public String getOaipmhChannel() {
    return oaipmhChannel;
  }

  @Override
  public void updated(Dictionary properties) throws ConfigurationException {
    if (properties == null)
      return;

    // Preview subtype
    final String preview = StringUtils.trimToNull((String) properties.get(OPT_PREVIEW_SUBTYPE));
    if (preview != null) {
      previewSubtype = preview;
      logger.info("Preview subtype is '{}'", previewSubtype);
    } else {
      logger.warn("No preview subtype configured, using '{}'", previewSubtype);
    }

    // Waveform subtype
    final String waveform = StringUtils.trimToNull((String) properties.get(OPT_WAVEFORM_SUBTYPE));
    if (waveform != null) {
      waveformSubtype = waveform;
      logger.info("Waveform subtype is '{}'", waveformSubtype);
    } else {
      logger.warn("No waveform subtype configured, using '{}'", waveformSubtype);
    }

    // Thumbnail publish flavor
    final String thumbnailPublishFlavor = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_PUBLISH_FLAVOR));
    if (thumbnailPublishFlavor != null) {
      this.thumbnailPublishFlavor = thumbnailPublishFlavor;
      logger.info("Thumbnail publish flavor is '{}'", thumbnailPublishFlavor);
    } else {
      logger.warn("No thumbnail publish flavor configured, using '{}'", this.thumbnailPublishFlavor);
    }

    // Thumbnail preview flavor
    final String thumbnailPreviewFlavor = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_PREVIEW_FLAVOR));
    if (thumbnailPreviewFlavor != null) {
      this.thumbnailPreviewFlavor = thumbnailPreviewFlavor;
      logger.info("Thumbnail preview flavor is '{}'", thumbnailPreviewFlavor);
    } else {
      logger.warn("No thumbnail preview flavor configured, using '{}'", this.thumbnailPreviewFlavor);
    }

    // Thumbnail source flavor subtype
    final String thumbnailSourceFlavorSubtype = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_SOURCE_FLAVOR_SUBTYPE));
    if (thumbnailSourceFlavorSubtype != null) {
      this.thumbnailSourceFlavorSubtype = thumbnailSourceFlavorSubtype;
      logger.info("Thumbnail source flavor subtype is '{}'", thumbnailSourceFlavorSubtype);
    } else {
      logger.warn("No thumbnail source flavor subtype configured, using '{}'", this.thumbnailSourceFlavorSubtype);
    }

    // Thumbnail source flavor type
    final String thumbnailSourceFlavorType = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_SOURCE_FLAVOR_TYPE));
    if (thumbnailSourceFlavorType != null) {
      this.thumbnailSourceFlavorType = thumbnailSourceFlavorType;
      logger.info("Thumbnail source flavor type is '{}'", thumbnailSourceFlavorType);
    } else {
      logger.warn("No thumbnail source flavor type configured, using '{}'", this.thumbnailSourceFlavorType);
    }

    // Thumbnail publish tags
    final String thumbnailPublishTags = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_PUBLISH_TAGS));
    if (thumbnailPublishTags != null) {
      this.thumbnailPublishTags = thumbnailPublishTags;
      logger.info("Thumbnail publish tags are '{}'", thumbnailPublishTags);
    } else {
      logger.warn("No thumbnail publish tags configured, using '{}'", this.thumbnailPublishTags);
    }

    // Thumbnail encoding profile
    final String thumbnailEncodingProfile = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_ENCODING_PROFILE));
    if (thumbnailEncodingProfile != null) {
      this.thumbnailEncodingProfile = thumbnailEncodingProfile;
      logger.info("Thumbnail encoding profile is '{}'", thumbnailEncodingProfile);
    } else {
      logger.warn("No thumbnail encoding profile configured, using '{}'", this.thumbnailEncodingProfile);
    }

    // Thumbnail default position
    final String thumbnailDefaultPosition = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_DEFAULT_POSITION));
    if (thumbnailDefaultPosition != null) {
      // This may throw a NumberFormatException. We should not use a default value in this case, but rather throw an
      // exception since using a default value may be unexpected.
      this.thumbnailDefaultPosition = Double.parseDouble(thumbnailDefaultPosition);
      logger.info("Thumbnail default position is '{}'", thumbnailDefaultPosition);
    } else {
      logger.warn("No thumbnail default position configured, using '{}'", this.thumbnailDefaultPosition);
    }

    // Thumbnail default track primary
    final String thumbnailDefaultTrackPrimary = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_DEFAULT_TRACK_PRIMARY));
    if (thumbnailDefaultTrackPrimary != null) {
      this.thumbnailDefaultTrackPrimary = thumbnailDefaultTrackPrimary;
      logger.info("Thumbnail default track primary is '{}'", thumbnailDefaultTrackPrimary);
    } else {
      logger.warn("No thumbnail default track primary configured, using '{}'", this.thumbnailDefaultTrackPrimary);
    }

    // Thumbnail default track secondary
    final String thumbnailDefaultTrackSecondary = StringUtils.trimToNull((String) properties.get(OPT_THUMBNAIL_DEFAULT_TRACK_SECONDARY));
    if (thumbnailDefaultTrackSecondary != null) {
      this.thumbnailDefaultTrackSecondary = thumbnailDefaultTrackSecondary;
      logger.info("Thumbnail default track secondary is '{}'", thumbnailDefaultTrackSecondary);
    } else {
      logger.warn("No thumbnail default track secondary configured, using '{}'", this.thumbnailDefaultTrackSecondary);
    }

    // SMIL catalog flavor
    final String smilCatalog = StringUtils.trimToNull((String) properties.get(OPT_SMIL_CATALOG_FLAVOR));
    if (smilCatalog != null) {
      smilCatalogFlavor = MediaPackageElementFlavor.parseFlavor(smilCatalog);
      logger.info("Smil catalg flavor is '{}'", smilCatalogFlavor);
    } else {
      logger.warn("No smil catalog flavor configured, using '{}'", smilCatalogFlavor);
    }

    // SMIL catalog tags
    final String[] smilCatalogTags = StringUtils.split((String) properties.get(OPT_SMIL_CATALOG_TAGS), ",");
    if (smilCatalogTags != null) {
      smilCatalogTagSet.clear();
      smilCatalogTagSet.addAll(Arrays.asList(smilCatalogTags));
      logger.info("Smil catalg tags are '{}'", StringUtils.join(smilCatalogTagSet, ","));
    } else {
      logger.warn("No smil catalog tags configured");
    }

    // SMIL silence flavor
    final String smilSilence = StringUtils.trimToNull((String) properties.get(OPT_SMIL_SILENCE_FLAVOR));
    if (smilSilence != null) {
      smilSilenceFlavor = MediaPackageElementFlavor.parseFlavor(smilSilence);
      logger.info("Smil silence flavor is '{}'", smilSilenceFlavor);
    } else {
      logger.warn("No smil silence flavor configured, using '{}'", smilSilenceFlavor);
    }

    // OAI-PMH channel
    final String oaiPmhChannel = StringUtils.trimToNull((String) properties.get(OPT_OAIPMH_CHANNEL));
    if (oaiPmhChannel != null) {
      this.oaipmhChannel = oaiPmhChannel;
      logger.info("OAI-PMH channel is '{}", oaiPmhChannel);
    } else {
      logger.warn("No OAI-PMH channel configured, using '{}", this.oaipmhChannel);
    }
  }
}
