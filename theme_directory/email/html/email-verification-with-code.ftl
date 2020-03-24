<#import "template.ftl" as layout>
<@layout.emailLayout>

<h2>${kcSanitize(msg("emailVerificationSubject"))}</h2>

${kcSanitize(msg("emailVerificationBodyHtml2",link, linkExpiration, realmName, "", layout.emailButton))?no_esc}

${kcSanitize(msg("emailVerificationBodyCodeHtml",code))?no_esc}

</@layout.emailLayout>

