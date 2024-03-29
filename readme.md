### Verify Email with Link or Code

This module copies "Verify Email" behaviour, but adds another option to verify with code.

## Setup 
Update your theme with values from theme_directory files

Code format can be configured in keycloak.conf:
```yaml
spi-required-action-VERIFY_EMAIL_WITH_CODE-code_format=digits-6
```

##### Available formats:
- digits-N
- lower-N
- upper-N
- alphanum-N

### Settings

**IMPORTANT**: You must also disable "Profile Validation" execution in your Registration flow.
Add new action: Authentication > Required actions, click "Register" button and select "Verify Email with Code". 
Then, disable default "Verify email" required action, and enable new "Verify Email with Code".
   