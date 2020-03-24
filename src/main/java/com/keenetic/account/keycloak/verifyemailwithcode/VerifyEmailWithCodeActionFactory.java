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

import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;


/**
 * @author <a href="mailto:hokum@dived.me">Andrey Kotov</a>
 */
public class VerifyEmailWithCodeActionFactory implements RequiredActionFactory {

  private static final VerifyEmailWithCodeAction SINGLETON = new VerifyEmailWithCodeAction();

  @Override
  public String getDisplayText() {
    return "Verify Email with Code";
  }

  @Override
  public RequiredActionProvider create(KeycloakSession keycloakSession) {
    return SINGLETON;
  }

  @Override
  public void init(Config.Scope scope) {
    SINGLETON.codeFormat = scope.get("code_format", "");
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

  }

  @Override
  public void close() {

  }

  @Override
  public String getId() {
    return "VERIFY_EMAIL_WITH_CODE";
  }
}
