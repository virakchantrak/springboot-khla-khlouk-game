package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.auth.security.PlayerPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentPlayerService {

    public UUID getPlayerId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        PlayerPrincipal principal =
                (PlayerPrincipal) authentication.getPrincipal();

        return principal.getPlayerId();
    }
}
