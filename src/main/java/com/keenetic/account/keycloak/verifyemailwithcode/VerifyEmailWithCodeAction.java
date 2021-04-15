/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keenetic.account.keycloak.verifyemailwithcode;

import org.jboss.logging.Logger;
import org.keycloak.authentication.*;
import org.keycloak.authentication.actiontoken.verifyemail.VerifyEmailActionToken;
import org.keycloak.common.util.RandomString;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.services.Urls;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import javax.ws.rs.core.*;

/** @author <a href="mailto:hokum@dived.me">Andrey Kotov</a> */
public class VerifyEmailWithCodeAction implements RequiredActionProvider {

  // References:
  // https://github.com/keycloak/keycloak/blob/master/services/src/main/java/org/keycloak/authentication/requiredactions/VerifyEmail.java
  // https://github.com/keycloak/keycloak/blob/master/services/src/main/java/org/keycloak/authentication/requiredactions/ConsoleVerifyEmail.java

  private static final Logger logger = Logger.getLogger(VerifyEmailWithCodeAction.class);
  String codeFormat;

  /**
   * Generates code
   * @param method String, like "alfanum-8"
   * @return String
   */
  static String generateCode(String method) {
    if (method == null) {
      method = "alfanum-6";
    }
    int codeLength = 8;
    String[] parts = method.split("-");
    if (parts.length == 2) {
      try {
        codeLength = Integer.parseInt(parts[1]);
      } catch (NumberFormatException ignored) {
        throw new IllegalArgumentException();
      }
    }
    String codeSource;
    switch (parts[0]) {
      case "lower": {
        codeSource = RandomString.lower;
        break;
      }
      case "upper": {
        codeSource = RandomString.upper;
        break;
      }
      case "digits": {
        codeSource = RandomString.digits;
        break;
      }
      default: {
        codeSource = RandomString.alphanum;
      }
    }
    return new RandomString(codeLength, new SecureRandom(), codeSource).nextString();
  }

  @Override
  public void evaluateTriggers(RequiredActionContext requiredActionContext) {}

  @Override
  public void requiredActionChallenge(RequiredActionContext requiredActionContext) {

    AuthenticationSessionModel authSession = requiredActionContext.getAuthenticationSession();

    if (requiredActionContext.getUser().isEmailVerified()) {
      requiredActionContext.success();
      authSession.removeAuthNote(Constants.VERIFY_EMAIL_KEY);
      authSession.removeAuthNote(Constants.VERIFY_EMAIL_CODE);
      return;
    }

    String email = requiredActionContext.getUser().getEmail();
    if (Validation.isBlank(email)) {
      requiredActionContext.ignore();
      return;
    }

    LoginFormsProvider loginFormsProvider = requiredActionContext.form();
    Response challenge;

    // Do not allow resending e-mail by simple page refresh, i.e. when e-mail sent, it should be
    // resent properly via email-verification endpoint
    if (!Objects.equals(authSession.getAuthNote(Constants.VERIFY_EMAIL_KEY), email)) {
      authSession.setAuthNote(Constants.VERIFY_EMAIL_KEY, email);

      EventBuilder event =
          requiredActionContext
              .getEvent()
              .clone()
              .event(EventType.SEND_VERIFY_EMAIL)
              .detail(Details.EMAIL, email);
      challenge =
          sendVerifyEmailWithCode(
              requiredActionContext.getSession(),
              loginFormsProvider,
              requiredActionContext.getUser(),
              requiredActionContext.getAuthenticationSession(),
              event);
    } else {
      challenge = loginFormsProvider.createForm("login-verify-email-code.ftl");
    }

    requiredActionContext.challenge(challenge);
  }

  private Response sendVerifyEmailWithCode(
      KeycloakSession session,
      LoginFormsProvider forms,
      UserModel user,
      AuthenticationSessionModel authSession,
      EventBuilder event)
      throws UriBuilderException, IllegalArgumentException {
    RealmModel realm = session.getContext().getRealm();
    UriInfo uriInfo = session.getContext().getUri();

    int validityInSecs =
        realm.getActionTokenGeneratedByUserLifespan(VerifyEmailActionToken.TOKEN_TYPE);
    int absoluteExpirationInSecs = Time.currentTime() + validityInSecs;

    String authSessionEncodedId =
        AuthenticationSessionCompoundId.fromAuthSession(authSession).getEncodedId();
    VerifyEmailActionToken token =
        new VerifyEmailActionToken(
            user.getId(),
            absoluteExpirationInSecs,
            authSessionEncodedId,
            user.getEmail(),
            authSession.getClient().getClientId());
    UriBuilder builder =
        Urls.actionTokenBuilder(
            uriInfo.getBaseUri(),
            token.serialize(session, realm, uriInfo),
            authSession.getClient().getClientId(),
            authSession.getTabId());
    String link = builder.build(realm.getName()).toString();
    // long expirationInMinutes = TimeUnit.SECONDS.toMinutes(validityInSecs);

    String code = generateCode(this.codeFormat);
    authSession.setAuthNote(Constants.VERIFY_EMAIL_CODE, code);

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("code", code);
    attributes.put("user", user);
    attributes.put("link", link);
    attributes.put("linkExpiration", validityInSecs);
    attributes.put("realmName", realm.getDisplayName());

    try {
      session
          .getProvider(EmailTemplateProvider.class)
          .setAuthenticationSession(authSession)
          .setRealm(realm)
          .setUser(user)
          // .sendVerifyEmail(link, expirationInMinutes);
          .send("emailVerificationSubject", "email-verification-with-code.ftl", attributes);
      event.success();
    } catch (EmailException e) {
      logger.error("Failed to send verification email", e);
      event.error(Errors.EMAIL_SEND_FAILED);
    }

    return forms.createForm("login-verify-email-code.ftl");
  }

  @Override
  public void processAction(RequiredActionContext requiredActionContext) {

    String code =
        requiredActionContext.getAuthenticationSession().getAuthNote(Constants.VERIFY_EMAIL_CODE);
    if (code == null) {
      requiredActionChallenge(requiredActionContext);
      return;
    }

    MultivaluedMap<String, String> formData =
        requiredActionContext.getHttpRequest().getDecodedFormParameters();
    String emailCode = formData.getFirst("code");

    if (emailCode == null) {
      requiredActionContext.getAuthenticationSession().removeAuthNote(Constants.VERIFY_EMAIL_KEY);
      requiredActionContext.getAuthenticationSession().removeAuthNote(Constants.VERIFY_EMAIL_CODE);
      requiredActionContext.form().setInfo("newCodeSent");
      requiredActionChallenge(requiredActionContext);
      return;
    }

    if (!code.equals(emailCode)) {
      LoginFormsProvider loginFormsProvider = requiredActionContext.form();
      loginFormsProvider.setError("invalidCode");
      Response challenge = loginFormsProvider.createForm("login-verify-email-code.ftl");
      requiredActionContext.challenge(challenge);
      return;
    }

    requiredActionContext.success();
    requiredActionContext.getUser().setEmailVerified(true);
  }

  @Override
  public void close() {}
}

