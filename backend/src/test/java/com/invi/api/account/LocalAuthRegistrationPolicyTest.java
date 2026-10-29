package com.invi.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invi.api.common.GlobalExceptionHandler;
import com.invi.api.common.HttpsTestOwnerBootstrap;
import com.invi.api.common.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LocalAuthController.class, properties = {
        "invi.auth.registration-enabled=false",
        "invi.frontend.base-url=https://test.example"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class LocalAuthRegistrationPolicyTest {

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @MockitoBean private LocalAuthService localAuthService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Test
    void disabledRegistrationRejectsValidAnonymousRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"owner@example.test","password":"test-password","name":"Owner"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Test signup is disabled."));

        verifyNoInteractions(localAuthService);
    }

    @Test
    void loginRemainsPublicWhenRegistrationIsDisabled() throws Exception {
        var request = new LocalAuthController.LoginRequest("owner@example.test", "test-password");
        when(localAuthService.login(request)).thenReturn(new LocalAuthController.TokenResponse("test-token"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"owner@example.test","password":"test-password"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token"));

        verify(localAuthService).login(request);
        verifyNoMoreInteractions(localAuthService);
    }

    @Test
    void explicitlyEnabledRegistrationPreservesNormalFlow() {
        var request = new LocalAuthController.RegisterRequest("new@example.test", "test-password", "New owner");
        var response = new LocalAuthController.TokenResponse("test-token");
        when(localAuthService.register(request)).thenReturn(response);

        assertThat(new LocalAuthController(localAuthService, true).register(request)).isSameAs(response);

        verify(localAuthService).register(request);
    }

    @Test
    void constructorPropertyDefaultsRegistrationToEnabled() throws Exception {
        var constructor = LocalAuthController.class.getConstructor(LocalAuthService.class, boolean.class);
        assertThat(constructor.getParameters()[1].getAnnotation(Value.class).value())
                .isEqualTo("${invi.auth.registration-enabled:true}");
    }

    @Test
    void flagPolicySliceDoesNotActivateHttpsTestOrLoadItsOwnerBootstrap() {
        assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("https-test");
        assertThat(context.getBeanNamesForType(HttpsTestOwnerBootstrap.class)).isEmpty();
    }
}
