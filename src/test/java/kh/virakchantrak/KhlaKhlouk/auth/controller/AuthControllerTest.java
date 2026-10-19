package kh.virakchantrak.KhlaKhlouk.auth.controller;

import kh.virakchantrak.KhlaKhlouk.auth.config.SecurityConfig;
import kh.virakchantrak.KhlaKhlouk.auth.service.AuthService;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.LoginAttemptService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {"jwt.secret=rSwGVXwk4Laqibfv+DEuqYVLEAhuK5MV5E0hgEqZCW528T9WsS633m0gQX5skNX3", "jwt.expiration=3600000"}
)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;
    @MockitoBean PlayerDetailsService playerDetailsService;
    @MockitoBean LoginAttemptService loginAttemptService;

    // ---------- login ----------

    @Test
    void loginWithEmptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() throws Exception {
        UserDetails user = User.withUsername("alice")
                .password("hashed")
                .roles("PLAYER")
                .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);
        when(jwtService.generateToken(user)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake-jwt"));
        verify(loginAttemptService).recordSuccess(any(), eq("alice"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtService);
        verify(loginAttemptService).registerAttempt(any(), eq("alice"));
        verify(loginAttemptService, never()).recordSuccess(any(), any());
    }

    // ---------- change password ----------

    @Test
    void changePasswordWithoutLoginReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"new-pass-1\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void changePasswordWithTooShortNewPasswordReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword")
                        .value("New password must be between 6 and 72 characters"));

        verifyNoInteractions(authService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void changePasswordWithValidRequestReturns204() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"new-pass-1\"}"))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(any());
    }

    @Test
    void loginIsRejectedWith429WhenTooManyAttempts() throws Exception {
        doThrow(new BusinessException(
                "TOO_MANY_LOGIN_ATTEMPTS",
                "Too many login attempts. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS))
                .when(loginAttemptService).registerAttempt(any(), eq("alice"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

        // the password is never even checked
        verifyNoInteractions(authenticationManager, jwtService);
    }

    @Test
    void oversizedLoginFieldsAreRejectedBeforeAnythingIsCounted() throws Exception {
        String longUsername = "a".repeat(101);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + longUsername + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());

        verifyNoInteractions(loginAttemptService, authenticationManager);
    }
}