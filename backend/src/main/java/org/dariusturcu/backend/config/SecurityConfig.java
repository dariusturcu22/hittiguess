package org.dariusturcu.backend.config;

import tools.jackson.databind.ObjectMapper;
import org.dariusturcu.backend.exception.ErrorResponse;
import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.dariusturcu.backend.security.oauth2.ReturnToOAuth2AuthorizationRequestResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@EnableWebSecurity
@Order(1)

public class SecurityConfig {

    public static final List<String> AUTHENTICATED_AUTH_ENDPOINTS = List.of(
            "/auth/2fa/setup",
            "/auth/2fa/confirm",
            "/auth/2fa/disable"
    );

    public static final List<String> PUBLIC_ALLOW_LIST = List.of(
            "/auth/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/api/enums/**",
            "/api/playlists/invites/*/preview",
            "/api/groups/invites/*/preview",
            "/api/users/*/avatar",
            "/api/ground-truth/**",
            "/actuator/health/**",
            "/health",
            "/error"
    );

    public static final List<String> CSRF_EXEMPT_ENDPOINTS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/2fa/verify",
            "/auth/verify-email",
            "/auth/resend-verification",
            "/auth/password-reset/**",
            "/auth/refresh",
            "/error",
            "/oauth2/**",
            "/login/oauth2/**"
    );

    public static final List<String> CORS_ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    public static final List<String> CORS_ALLOWED_HEADERS = List.of(
            "Content-Type",
            "Accept",
            "X-XSRF-TOKEN",
            "X-Request-Id",
            "Authorization"
    );

    public static final List<String> CORS_EXPOSED_HEADERS = List.of(
            "X-Request-Id"
    );

    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final UserDetailsService userDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final ReturnToOAuth2AuthorizationRequestResolver returnToAuthorizationRequestResolver;
    private final ObjectMapper objectMapper;
    private final List<String> allowedFrontendOrigins;
    private final int managementServerPort;

    @Value("${app.env:dev}")
    private String appEnv = "dev";

    @Value("${app.cookie-domain:}")
    private String cookieDomain = "";

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            UserDetailsService userDetailsService,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            ReturnToOAuth2AuthorizationRequestResolver returnToAuthorizationRequestResolver,
            ObjectMapper objectMapper,
            @Value("${frontend.allowed-origins}") List<String> allowedFrontendOrigins,
            @Value("${management.server.port:8081}") int managementServerPort
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.userDetailsService = userDetailsService;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.returnToAuthorizationRequestResolver = returnToAuthorizationRequestResolver;
        this.objectMapper = objectMapper;
        if (allowedFrontendOrigins != null && allowedFrontendOrigins.contains("*")) {
            throw new IllegalStateException("CORS allowed origins cannot contain wildcard '*' when allowCredentials is true");
        }
        this.allowedFrontendOrigins = allowedFrontendOrigins;
        this.managementServerPort = managementServerPort;
    }

    // The frontend reads this cookie from document.cookie to echo it as a header, so on a
    // sibling subdomain it must carry the shared parent domain, or the browser hides it.
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        boolean isProduction = "prod".equals(appEnv);
        csrfTokenRepository.setCookieCustomizer(cookie -> {
            if (!cookieDomain.isBlank()) {
                cookie.domain(cookieDomain);
            }
            if (isProduction) {
                cookie.secure(true);
                cookie.sameSite("None");
            }
        });
        return csrfTokenRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfHandler)
                        // Only the /auth endpoints reached before a session exists skip CSRF.
                        // The ones acting on the signed-in account (2FA setup, confirm,
                        // disable, and logout) need the token like every other mutation,
                        // since production cookies are SameSite=None.
                        .ignoringRequestMatchers(CSRF_EXEMPT_ENDPOINTS.toArray(String[]::new))
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTHENTICATED_AUTH_ENDPOINTS.toArray(String[]::new)).authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(request -> request.getLocalPort() == managementServerPort).permitAll()
                        .requestMatchers(PUBLIC_ALLOW_LIST.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestRepository(authorizationRequestRepository)
                                .authorizationRequestResolver(returnToAuthorizationRequestResolver))
                        .userInfoEndpoint(userInfoEndpointConfig -> userInfoEndpointConfig
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedFrontendOrigins);
        configuration.setAllowedMethods(CORS_ALLOWED_METHODS);
        configuration.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        configuration.setExposedHeaders(CORS_EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return authenticationProvider;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.FORBIDDEN.value(),
                    accessDeniedException.getMessage(),
                    LocalDateTime.now()
            );
            objectMapper.writeValue(response.getWriter(), errorResponse);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
