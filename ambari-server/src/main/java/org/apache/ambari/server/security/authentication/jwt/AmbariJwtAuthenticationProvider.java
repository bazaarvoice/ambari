/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.security.authentication.jwt;

import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.orm.entities.UserAuthenticationEntity;
import org.apache.ambari.server.orm.entities.UserEntity;
import org.apache.ambari.server.security.authentication.AmbariAuthenticationException;
import org.apache.ambari.server.security.authentication.AmbariAuthenticationProvider;
import org.apache.ambari.server.security.authentication.AmbariUserAuthentication;
import org.apache.ambari.server.security.authentication.UserNotFoundException;
import org.apache.ambari.server.security.authorization.User;
import org.apache.ambari.server.security.authorization.UserAuthenticationType;
import org.apache.ambari.server.security.authorization.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import com.google.inject.Inject;

/**
 * AmbariLocalAuthenticationProvider is an {@link org.springframework.security.authentication.AuthenticationProvider}
 * implementation used to authenticate users using username and password details from the local Ambari database.
 * <p>
 * Users will fail to authenticate, even if they supply the correct credentials if the account is locked out
 * by being disabled or locked due to too many consecutive failure.
 */
public class AmbariJwtAuthenticationProvider extends AmbariAuthenticationProvider {
  private static final Logger LOG = LoggerFactory.getLogger(AmbariJwtAuthenticationProvider.class);

  /**
   * Helper object to provide logic for working with users.
   */
  private Users users;

  /**
   * Constructor.
   *
   * @param users         the users helper
   * @param configuration the configuration
   */
  @Inject
  public AmbariJwtAuthenticationProvider(Users users, Configuration configuration) {
    super(users, configuration);
    this.users = users;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String userName = authentication.getName().trim();

    UserEntity userEntity;
    try {
      userEntity = getUserEntity(userName);

      if (userEntity == null) {
        LOG.info("User not found: {}", userName);
        throw new UserNotFoundException(userName, "Cannot find user from JWT. Please, ensure LDAP is configured and users are synced.");
      }
    } catch (UserNotFoundException e) {
      throw new UserNotFoundException(userName, "Cannot find user from JWT. Please, ensure LDAP is configured and users are synced.", e);
    }

    if (authentication.getCredentials() == null) {
      LOG.info("Authentication failed: no token provided: {}", userName);
      throw new AmbariAuthenticationException(userName, "Unexpected error due to missing JWT token");
    }

    // If the user was found and allowed to log in, make sure that user is allowed to authentcate using a JWT token.
    boolean authOK = false;
    UserAuthenticationEntity authenticationEntity = getAuthenticationEntity(userEntity, UserAuthenticationType.JWT);
    if (authenticationEntity != null) {
      authOK = true;
    } else {
      // TODO: Determine if LDAP users can authenticate using JWT - for now we assume yes.
      // If a JWT entity was not found, see if an LDAP entity exists. If so, this user was synced
      // with a remote server and this should be allowed to authenticate using JWT
      authenticationEntity = getAuthenticationEntity(userEntity, UserAuthenticationType.LDAP);

      if (authenticationEntity != null) {
        try {
          users.addJWTAuthentication(userEntity, userName);
          authOK = true;
        } catch (AmbariException e) {
          LOG.error(String.format("Failed to add the JWT authentication method for %s: %s", userName, e.getLocalizedMessage()), e);
          throw new AmbariAuthenticationException(userName, "Unexpected error has occurred", e);
        }
      }
    }

    if (authOK) {
      // The user was  authenticated, return the authenticated user object
      LOG.debug("Authentication succeeded - a matching user was found: {}", userName);
      User user = new User(userEntity);
      Authentication auth = new AmbariUserAuthentication(authentication.getCredentials().toString(), user, users.getUserAuthorities(userEntity));
      auth.setAuthenticated(true);
      return auth;
    } else {
      // The user was not authenticated, fail
      LOG.debug("Authentication failed: password does not match stored value: {}", userName);
      throw new UserNotFoundException(userName, "Cannot find user from JWT. Please, ensure LDAP is configured and users are synced.");
    }
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return JwtAuthenticationToken.class.isAssignableFrom(authentication);
  }
}