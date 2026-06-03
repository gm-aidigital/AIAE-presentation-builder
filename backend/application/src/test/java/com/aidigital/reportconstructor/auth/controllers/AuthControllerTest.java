package com.aidigital.reportconstructor.auth.controllers;

import com.aidigital.reportconstructor.auth.mappers.UserMapperImpl;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.security.AuthProperties;
import com.aidigital.reportconstructor.security.ClerkJwtClaimsValidator;
import com.aidigital.reportconstructor.security.ClerkPublishableKeyDecoder;
import com.aidigital.reportconstructor.security.CompanyEmailDomainAuthorizationManager;
import com.aidigital.reportconstructor.security.SecurityConfig;
import com.aidigital.reportconstructor.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@EnableConfigurationProperties({AuthProperties.class, SecurityProperties.class})
@Import({
    SecurityConfig.class,
    AppUserFactory.class,
    UserMapperImpl.class,
    ClerkJwtClaimsValidator.class,
    ClerkPublishableKeyDecoder.class,
    CompanyEmailDomainAuthorizationManager.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldRejectUnauthenticatedRequestToAuthMeTest() throws Exception {
        ResultActions response = mvc.perform(get("/api/v1/auth/me"));
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUserPayloadFromJwtClaimsTest() throws Exception {
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .with(jwt().jwt(j -> j
                .subject("user_123")
                .claim("user_id", "user_123")
                .claim("email", "alice@aidigital.com")
                .claim("full_name", "Alice Example"))));

        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("user_123"))
                .andExpect(jsonPath("$.email").value("alice@aidigital.com"))
                .andExpect(jsonPath("$.full_name").value("Alice Example"));
    }

    @Test
    void shouldRejectInvalidOrExpiredTokenTest() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("invalid token"));
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer not-a-real-token"));
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidValidJwtWithForeignEmailDomainTest() throws Exception {
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .with(jwt().jwt(j -> j
                .subject("user_999")
                .claim("user_id", "user_999")
                .claim("email", "mallory@attacker.example"))));

        response.andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidValidJwtWithoutEmailTest() throws Exception {
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .with(jwt().jwt(j -> j
                .subject("user_999")
                .claim("user_id", "user_999"))));

        response.andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidValidJwtWithSubdomainEmailTest() throws Exception {
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .with(jwt().jwt(j -> j
                .subject("user_999")
                .claim("user_id", "user_999")
                .claim("email", "bob@team.aidigital.com"))));

        response.andExpect(status().isForbidden());
    }
}
