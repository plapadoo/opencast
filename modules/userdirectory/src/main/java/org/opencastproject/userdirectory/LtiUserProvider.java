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

  public LtiUserProvider() {
    System.out.println("test");
  }

  /** The constant indicating that a provider should be consulted for all organizations */


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
    System.out.println("Updated");
  }
}
