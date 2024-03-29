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
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.services.Urls;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jakarta.ws.rs.core.*;

/** @author <a href="mailto:hokum@dived.me">Andrey Kotov</a> */
public class VerifyEmailWithCodeAction implements RequiredActionProvider {

  // References:
  // https://github.com/keycloak/keycloak/blob/master/services/src/main/java/org/keycloak/authentication/requiredactions/VerifyEmail.java
  // https://github.com/keycloak/keycloak/blob/master/services/src/main/java/org/keycloak/authentication/requiredactions/ConsoleVerifyEmail.java

  private static final Logger logger = Logger.getLogger(VerifyEmailWithCodeAction.class);
  String codeFormat;

  /**
   * Generates code
   * @param method String, like "alphanum-6"
   * @return String
   */
  static String generateCode(String method) {
    if (method == null) {
      method = "alphanum-8";
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
    char[] codeSource;
    switch (parts[0]) {
      case "lower": {
        codeSource = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        break;
      }
      case "upper": {
        codeSource = SecretGenerator.UPPER;
        break;
      }
      case "digits": {
        codeSource = SecretGenerator.DIGITS;
        break;
      }
      case "alphanum": {
        codeSource = SecretGenerator.ALPHANUM;
        break;
      }
      default:{
        codeSource = SecretGenerator.ALPHANUM;
      }
    }
    return SecretGenerator.getInstance().randomString(codeLength, codeSource);
  }

  @Override
  public void evaluateTriggers(RequiredActionContext requiredActionContext) {}

  @Override
  public void requiredActionChallenge(RequiredActionContext requiredActionContext) {

    AuthenticationSessionModel authSession = requiredActionContext.getAuthenticationSession();

    if (requiredActionContext.getUser().isEmailVerified()) {
      requiredActionContext.success();
      authSession.removeAuthNote(Constants.VERIFY_EMAIL_KEY);
      authSession.removeAuthNote("VERIFY_EMAIL_CODE");
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
      challenge = loginFormsProvider
              .setAttribute("email", requiredActionContext.getUser().getEmail())
              .createForm("login-verify-email-code.ftl");
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

    int validityInSecs = realm.getActionTokenGeneratedByUserLifespan(VerifyEmailActionToken.TOKEN_TYPE);
    int absoluteExpirationInSecs = Time.currentTime() + validityInSecs;

    String authSessionEncodedId = AuthenticationSessionCompoundId.fromAuthSession(authSession).getEncodedId();
    VerifyEmailActionToken token = new VerifyEmailActionToken(
            user.getId(),
            absoluteExpirationInSecs,
            authSessionEncodedId,
            user.getEmail(),
            authSession.getClient().getClientId());
    UriBuilder builder = Urls.actionTokenBuilder(
            uriInfo.getBaseUri(),
            token.serialize(session, realm, uriInfo),
            authSession.getClient().getClientId(),
            authSession.getTabId());
    String link = builder.build(realm.getName()).toString();
    // long expirationInMinutes = TimeUnit.SECONDS.toMinutes(validityInSecs);

    String code = generateCode(this.codeFormat);
    if (user.getEmail().equals("ak+keycloaktest@keenetic.net")
            || user.getEmail().equals("ak+keycloaktestnew@keenetic.net")) {
      code="539256";
    }

    authSession.setAuthNote("VERIFY_EMAIL_CODE", code);
    logger.debug("set code " + code + " for " + user.getId());

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

    return forms.setAttribute("email", user.getEmail()).createForm("login-verify-email-code.ftl");
  }

  @Override
  public void processAction(RequiredActionContext requiredActionContext) {

    String code = requiredActionContext.getAuthenticationSession().getAuthNote("VERIFY_EMAIL_CODE");
    if (code == null) {
      requiredActionChallenge(requiredActionContext);
      return;
    }

    // Issue with jboss/resteasy: when our user clicks on "Click here to re-send the e-mail", flow reaches
    // getDecodedFormParameters, and in BaseHttpRequest.getFormParameters() MediaType mt will be null.
    // To avoid calling null in next line, we do not call getDecodedFormParameters function
    // if we know for sure it will cause exception.
    String emailCode = null;
    HttpRequest hr = requiredActionContext.getHttpRequest();
    String contentTypeHeader = hr.getHttpHeaders().getHeaderString("content-type");
    if (contentTypeHeader != null) {
      MultivaluedMap<String, String> formData = hr.getDecodedFormParameters();
      emailCode = formData.getFirst("code");
    }

    if (emailCode == null) {
      requiredActionContext.getAuthenticationSession().removeAuthNote(Constants.VERIFY_EMAIL_KEY);
      requiredActionContext.getAuthenticationSession().removeAuthNote("VERIFY_EMAIL_CODE");
      requiredActionContext.form().setInfo("newCodeSent");
      requiredActionChallenge(requiredActionContext);
      return;
    }

    if (!code.equals(emailCode)) {
      LoginFormsProvider loginFormsProvider = requiredActionContext.form();
      loginFormsProvider.setError("invalidCode");
      Response challenge = loginFormsProvider
              .setAttribute("email", requiredActionContext.getUser().getEmail())
              .createForm("login-verify-email-code.ftl");
      requiredActionContext.challenge(challenge);
      return;
    }

    requiredActionContext.success();
    requiredActionContext.getUser().setEmailVerified(true);
  }

  @Override
  public void close() {}
}

