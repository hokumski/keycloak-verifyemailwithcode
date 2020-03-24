### Verify Email with Link or Code

This module copies "Verify Email" behaviour, but adds another option to verify with code.

Update your theme with values from theme_directory files

Code format can be configured with standalone.xml:
```xml
<provider name="VERIFY_EMAIL_WITH_CODE" enabled="true">
    <properties>
        <property name="code_format" value="digits-6"/>
    </properties>
</provider>
``` 

##### Available formats:
- digits-N
-   