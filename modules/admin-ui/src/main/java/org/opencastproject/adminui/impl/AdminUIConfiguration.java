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
  public static final String OPT_SOURCE_TRACK_PRESENTER_FLAVOR = "sourcetrack.presenter.flavor";
  public static final String OPT_SOURCE_TRACK_PRESENTATION_FLAVOR = "sourcetrack.presentation.flavor";

  private String previewSubtype = "preview";
  private String waveformSubtype = "waveform";
  private Set<String> smilCatalogTagSet = new HashSet<String>();
  private MediaPackageElementFlavor smilCatalogFlavor = new MediaPackageElementFlavor("smil", "cutting");
  private MediaPackageElementFlavor smilSilenceFlavor = new MediaPackageElementFlavor("*", "silence");
  private MediaPackageElementFlavor sourceTrackPresenterFlavor = new MediaPackageElementFlavor("presenter", "source");
  private MediaPackageElementFlavor sourceTrackPresentationFlavor = new MediaPackageElementFlavor("presentation", "source");

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

  public MediaPackageElementFlavor getSourceTrackPresenterFlavor() {
    return sourceTrackPresenterFlavor;
  }

  public MediaPackageElementFlavor getSourceTrackPresentationFlavor() {
    return sourceTrackPresentationFlavor;
  }

  @Override
  public void updated(Dictionary properties) throws ConfigurationException {
    if (properties == null)
      return;

    // Preview subtype
    String preview = StringUtils.trimToNull((String) properties.get(OPT_PREVIEW_SUBTYPE));
    if (preview != null) {
      previewSubtype = preview;
      logger.info("Preview subtype is '{}'", previewSubtype);
    } else {
      logger.warn("No preview subtype configured, using '{}'", previewSubtype);
    }

    // Waveform subtype
    String waveform = StringUtils.trimToNull((String) properties.get(OPT_WAVEFORM_SUBTYPE));
    if (waveform != null) {
      waveformSubtype = waveform;
      logger.info("Waveform subtype is '{}'", waveformSubtype);
    } else {
      logger.warn("No waveform subtype configured, using '{}'", waveformSubtype);
    }

    // SMIL catalog flavor
    String smilCatalog = StringUtils.trimToNull((String) properties.get(OPT_SMIL_CATALOG_FLAVOR));
    if (smilCatalog != null) {
      smilCatalogFlavor = MediaPackageElementFlavor.parseFlavor(smilCatalog);
      logger.info("Smil catalg flavor is '{}'", smilCatalogFlavor);
    } else {
      logger.warn("No smil catalog flavor configured, using '{}'", smilCatalogFlavor);
    }

    // SMIL catalog tags
    String[] smilCatalogTags = StringUtils.split((String) properties.get(OPT_SMIL_CATALOG_TAGS), ",");
    if (smilCatalogTags != null) {
      smilCatalogTagSet.clear();
      smilCatalogTagSet.addAll(Arrays.asList(smilCatalogTags));
      logger.info("Smil catalg tags are '{}'", StringUtils.join(smilCatalogTagSet, ","));
    } else {
      logger.warn("No smil catalog tags configured");
    }

    // SMIL silence flavor
    String smilSilence = StringUtils.trimToNull((String) properties.get(OPT_SMIL_SILENCE_FLAVOR));
    if (smilSilence != null) {
      smilSilenceFlavor = MediaPackageElementFlavor.parseFlavor(smilSilence);
      logger.info("Smil silence flavor is '{}'", smilSilenceFlavor);
    } else {
      logger.warn("No smil silence flavor configured, using '{}'", smilSilenceFlavor);
    }

    final String sourceTrackPresenter = StringUtils.trimToNull((String) properties.get(OPT_SOURCE_TRACK_PRESENTER_FLAVOR));
    if (sourceTrackPresenter != null) {
      sourceTrackPresenterFlavor = MediaPackageElementFlavor.parseFlavor(sourceTrackPresenter);
      logger.info("Source track presenter flavor is '{}'", sourceTrackPresenterFlavor);
    } else {
      logger.warn("No source track presenter configured, using '{}'", sourceTrackPresenterFlavor);
    }

    final String sourceTrackPresentation = StringUtils.trimToNull((String) properties.get(OPT_SOURCE_TRACK_PRESENTATION_FLAVOR));
    if (sourceTrackPresentation != null) {
      sourceTrackPresentationFlavor = MediaPackageElementFlavor.parseFlavor(sourceTrackPresentation);
      logger.info("Source track presentation flavor is '{}'", sourceTrackPresentationFlavor);
    } else {
      logger.warn("No source track presentation configured, using '{}'", sourceTrackPresentationFlavor);
    }
  }
}
