package kh.virakchantrak.KhlaKhlouk.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.ChangePasswordRequest;
import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.LoginRequest;
import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.LoginResponse;
import kh.virakchantrak.KhlaKhlouk.auth.service.AuthService;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.LoginAttemptService;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest
    ) {
        String ip = httpRequest.getRemoteAddr();

        // counted BEFORE the password is checked; throws 429 when over the limit
        loginAttemptService.registerAttempt(ip, request.getUsername());

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

        // only reached when the password was correct
        loginAttemptService.recordSuccess(ip, request.getUsername());

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
