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

package org.opencastproject.userdirectory;

import org.opencastproject.security.api.JaxbOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserProvider;

import org.osgi.service.cm.ManagedService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;

public class LtiUserProvider implements UserProvider, ManagedService {
  public static final String PROVIDER_NAME = "lti";

  @Override
  public String getName() {
    return PROVIDER_NAME;
  }

  @Override
  public Iterator<User> getUsers() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return Collections.emptyIterator();
  }

  @Override
  public User loadUser(String userName) {
    if (!userName.startsWith("lti:")) {
      return null;
    }
    final JaxbOrganization org = new JaxbOrganization("mh_default_org");
    return new JaxbUser(userName, PROVIDER_NAME, org, new JaxbRole("ROLE_OAUTH_USER", org),
            new JaxbRole("ROLE_USER", org), new JaxbRole("ROLE_ANONYMOUS", org));
  }

  @Override
  public long countUsers() {
    return 0;
  }

  @Override
  public String getOrganization() {
    return "*";
  }

  @Override
  public Iterator<User> findUsers(String query, int offset, int limit) {
    return Collections.emptyIterator();
  }

  @Override
  public void invalidate(String userName) {

  }

  @Override
  public void updated(Dictionary<String, ?> dictionary) {
  }
}
