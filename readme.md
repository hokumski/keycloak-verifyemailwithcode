### Verify Email with Link or Code

This module copies "Verify Email" behaviour, but adds another option to verify with code.

## Setup 
Update your theme with values from theme_directory files

Code format can be configured with standalone.xml:
```xml
<spi name="required-action">
    <provider name="VERIFY_EMAIL_WITH_CODE" enabled="true">
        <properties>
            <property name="code_format" value="digits-6"/>
        </properties>
    </provider>
</spi>
``` 

##### Available formats:
- digits-N

### Settings

You must also disable "Profile Validation" execution in your Registration flow.
Add new action: Authentication > Required actions, click "Register" button and select "New Email with Code". 
Then, disable default "Verify email" required action, and enable new "Verify Email with Code".
   