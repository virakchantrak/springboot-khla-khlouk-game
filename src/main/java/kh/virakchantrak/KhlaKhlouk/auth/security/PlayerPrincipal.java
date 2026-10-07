package kh.virakchantrak.KhlaKhlouk.auth.security;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public class PlayerPrincipal implements UserDetails {

    @Getter
    private final UUID playerId;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public PlayerPrincipal(
            UUID playerId,
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.playerId = playerId;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    @Override
    public @NullMarked Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public @NullMarked  String getUsername() {
        return username;
    }
}