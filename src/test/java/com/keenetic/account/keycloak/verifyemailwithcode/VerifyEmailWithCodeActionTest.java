package com.keenetic.account.keycloak.verifyemailwithcode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class VerifyEmailWithCodeActionTest {

    @Test
    public void generateCode() {

        String code1 = VerifyEmailWithCodeAction.generateCode("digits-6");
        assertEquals(6, code1.length());

        String code2 = VerifyEmailWithCodeAction.generateCode("lower-12");
        assertEquals(12, code2.length());
        assertEquals(code2.toLowerCase(), code2);

        String code3 = VerifyEmailWithCodeAction.generateCode("");
        assertEquals(8, code3.length());

    }

}
