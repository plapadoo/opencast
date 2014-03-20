/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.opencastproject.job.api;

import static org.opencastproject.util.data.Monadics.mlist;

import org.opencastproject.serviceregistry.api.IncidentService;
import org.opencastproject.serviceregistry.api.IncidentServiceException;
import org.opencastproject.util.NotFoundException;

import java.util.List;
import java.util.Locale;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "incidentDigestList", namespace = "http://job.opencastproject.org")
@XmlRootElement(name = "incidentDigestList", namespace = "http://job.opencastproject.org")
public final class JaxbIncidentDigestList {
  // CHECKSTYLE:OFF
  @XmlElement(name = JaxbIncidentUtil.ELEM_NESTED_INCIDENT)
  private List<JaxbIncidentDigest> incidents;

  /** Constructor for JAXB */
  public JaxbIncidentDigestList() {
  }

  public JaxbIncidentDigestList(IncidentService svc, Locale locale, List<Incident> incidents)
          throws IncidentServiceException, NotFoundException {
    this.incidents = mlist(incidents).map(JaxbIncidentDigest.mkFn(svc, locale)).value();
  }
}
