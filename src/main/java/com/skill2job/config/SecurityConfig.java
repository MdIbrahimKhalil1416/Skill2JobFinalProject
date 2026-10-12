package com.skill2job.config;

import com.skill2job.repository.UserRepository;
import com.skill2job.security.JwtAuthenticationFilter;
import com.skill2job.security.JwtUtil;
import com.skill2job.security.RestAuthHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security.
 * - Static pages, auth endpoints, Swagger, health and public GET lists are open.
 * - Everything else under /api needs a valid token; roles are checked with @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUtil jwtUtil,
                                                   UserRepository userRepository,
                                                   RestAuthHandlers authHandlers) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)      // safe: no cookies, token is sent in a header
            .cors(cors -> { })                          // uses the CorsConfigurationSource bean
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(authHandlers)
                .accessDeniedHandler(authHandlers))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/verify-otp",
                        "/api/auth/resend-otp", "/api/auth/forgot-password", "/api/auth/reset-password",
                        "/api/health").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/skills", "/api/skills/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/companies/**").permitAll()      // incl. /{id}/reviews
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/api/payments/sslcommerz/**").permitAll() // called directly by SSLCommerz, no JWT
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()               // static frontend files
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userRepository),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
