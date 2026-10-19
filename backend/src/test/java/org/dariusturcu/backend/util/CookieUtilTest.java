package org.dariusturcu.backend.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    private static final String SHARED_PARENT_DOMAIN = "hittiguess.com";
    private static final long ONE_HOUR_SECONDS = 3600;
    private static final String REFRESH_COOKIE_PATH = "/auth/refresh";

    private CookieUtil cookieUtil;

    @BeforeEach
    void setUp() {
        cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "appEnv", "prod");
    }

    @Test
    void cookiesStayHostOnlyWhenNoCookieDomainIsConfigured() {
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie("token", ONE_HOUR_SECONDS);

        assertThat(accessCookie.getDomain()).isNull();
    }

    @Test
    void everyAuthCookieCarriesTheConfiguredParentDomain() {
        ReflectionTestUtils.setField(cookieUtil, "cookieDomain", SHARED_PARENT_DOMAIN);

        assertThat(cookieUtil.createAccessTokenCookie("token", ONE_HOUR_SECONDS).getDomain())
                .isEqualTo(SHARED_PARENT_DOMAIN);
        assertThat(cookieUtil.createRefreshTokenCookie("token", ONE_HOUR_SECONDS).getDomain())
                .isEqualTo(SHARED_PARENT_DOMAIN);
        assertThat(cookieUtil.createSessionHintCookie(ONE_HOUR_SECONDS).getDomain())
                .isEqualTo(SHARED_PARENT_DOMAIN);
    }

    @Test
    void deletingACookieUsesTheSameDomainSoTheBrowserRemovesIt() {
        ReflectionTestUtils.setField(cookieUtil, "cookieDomain", SHARED_PARENT_DOMAIN);

        ResponseCookie deletion = cookieUtil.deleteCookie("refresh_token", REFRESH_COOKIE_PATH);

        assertThat(deletion.getDomain()).isEqualTo(SHARED_PARENT_DOMAIN);
        assertThat(deletion.getMaxAge().isZero()).isTrue();
    }

    @Test
    void theDomainDoesNotChangeTheOtherCookieAttributes() {
        ReflectionTestUtils.setField(cookieUtil, "cookieDomain", SHARED_PARENT_DOMAIN);

        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie("token", ONE_HOUR_SECONDS);

        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.isSecure()).isTrue();
        assertThat(refreshCookie.getSameSite()).isEqualTo("None");
        assertThat(refreshCookie.getPath()).isEqualTo(REFRESH_COOKIE_PATH);
    }
}
