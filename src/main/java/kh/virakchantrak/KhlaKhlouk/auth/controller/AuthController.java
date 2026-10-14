package kh.virakchantrak.KhlaKhlouk.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kh.virakchantrak.KhlaKhlouk.auth.audit.SecurityAudit;
import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.ChangePasswordRequest;
import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.LoginRequest;
import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.LoginResponse;
import kh.virakchantrak.KhlaKhlouk.auth.service.AuthService;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.LoginAttemptService;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.springframework.security.core.AuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = httpRequest.getRemoteAddr();
        String username = request.getUsername();

        try {
            loginAttemptService.registerAttempt(ip, username);
        } catch (BusinessException e) {
            SecurityAudit.loginBlocked(ip, username);
            throw e;
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            SecurityAudit.loginFailed(ip, username);
            throw e;
        }

        loginAttemptService.recordSuccess(ip, username);
        SecurityAudit.loginSucceeded(ip, username);

        String token = jwtService.generateToken(
                (UserDetails) authentication.getPrincipal()
        );

        return new LoginResponse(token);
    }

    @PreAuthorize("hasRole('PLAYER')")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(request);
    }
}
