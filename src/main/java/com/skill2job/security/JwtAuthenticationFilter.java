package com.skill2job.security;

import com.skill2job.entity.User;
import com.skill2job.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reads "Authorization: Bearer <token>", validates it and puts the user into the SecurityContext.
 * The user is re-loaded from the database so a deactivated account is rejected immediately.
 * Not a @Component on purpose: it is added only to the security chain in SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            jwtUtil.parse(token)
                    .flatMap(info -> userRepository.findById(info.userId())
                            .filter(User::isActive)
                            .filter(user -> issuedAfterPasswordChange(info.issuedAt(), user.getPasswordChangedAt())))
                    .ifPresent(user -> {
                        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole());
                        var auth = new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    /**
     * A token issued before the user's last password change is no longer accepted. JWT "iat" has
     * one-second precision, so the change time is truncated to seconds before comparing.
     */
    static boolean issuedAfterPasswordChange(Instant issuedAt, Instant passwordChangedAt) {
        if (passwordChangedAt == null) {
            return true;
        }
        if (issuedAt == null) {
            return false;
        }
        return !issuedAt.isBefore(passwordChangedAt.truncatedTo(ChronoUnit.SECONDS));
    }
}
