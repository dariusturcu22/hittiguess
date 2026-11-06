package org.dariusturcu.backend.security.oauth2;

import java.util.Map;

public class GoogleOAuth2UserInfo extends OAuth2UserInfo {
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        return attributes.get("sub").toString();
    }

    @Override
    public String getName() {
        return attributes.get("name").toString();
    }

    @Override
    public String getEmail() {
        return attributes.get("email").toString();
    }

    @Override
    public boolean isEmailVerified() {
        return Boolean.parseBoolean(String.valueOf(attributes.get(EMAIL_VERIFIED_CLAIM)));
    }

    @Override
    public String getImageUrl() {
        Object picture = attributes.get("picture");
        return picture != null ? picture.toString() : null;
    }
}
