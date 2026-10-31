package org.dariusturcu.backend.config;

import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.dariusturcu.backend.security.oauth2.ReturnToOAuth2AuthorizationRequestResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CsrfCookieDomainTest {

    private static final String SHARED_PARENT_DOMAIN = "hittiguess.com";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Mock
    private OAuth2AuthenticationFailureHandler failureHandler;

    @Mock
    private HttpCookieOAuth2AuthorizationRequestRepository requestRepository;

    @Mock
    private ReturnToOAuth2AuthorizationRequestResolver requestResolver;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void csrfCookieStaysHostOnlyWithoutACookieDomain() {
        Cookie cookie = savedCsrfCookie(securityConfig());

        assertThat(cookie.getName()).isEqualTo(CSRF_COOKIE_NAME);
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void csrfCookieCarriesTheSharedParentDomainSoTheFrontendCanRead() {
        SecurityConfig securityConfig = securityConfig();
        ReflectionTestUtils.setField(securityConfig, "cookieDomain", SHARED_PARENT_DOMAIN);

        Cookie cookie = savedCsrfCookie(securityConfig);

        assertThat(cookie.getDomain()).isEqualTo(SHARED_PARENT_DOMAIN);
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void csrfCookieInProductionCarriesSecureAndSameSiteNoneWithoutHttpOnly() {
        SecurityConfig securityConfig = securityConfig();
        ReflectionTestUtils.setField(securityConfig, "appEnv", "prod");
        ReflectionTestUtils.setField(securityConfig, "cookieDomain", SHARED_PARENT_DOMAIN);

        Cookie cookie = savedCsrfCookie(securityConfig);

        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("None");
        assertThat(cookie.getDomain()).isEqualTo(SHARED_PARENT_DOMAIN);
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void csrfCookieInDevelopmentOmitsSecureAndSameSiteNone() {
        SecurityConfig securityConfig = securityConfig();
        ReflectionTestUtils.setField(securityConfig, "appEnv", "dev");

        Cookie cookie = savedCsrfCookie(securityConfig);

        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isNull();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    private Cookie savedCsrfCookie(SecurityConfig securityConfig) {
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);
        return response.getCookie(CSRF_COOKIE_NAME);
    }

    private SecurityConfig securityConfig() {
        return new SecurityConfig(
                jwtAuthenticationFilter,
                rateLimitingFilter,
                userDetailsService,
                customOAuth2UserService,
                successHandler,
                failureHandler,
                requestRepository,
                requestResolver,
                objectMapper,
                List.of("https://www.hittiguess.com"), 8081);
    }
}
