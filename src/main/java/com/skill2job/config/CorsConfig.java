package com.skill2job.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The frontend is served by the same app, so CORS is only needed if you call the API
 * from another origin (e.g. a separate React app). Set CORS_ORIGINS="https://a.com,https://b.com".
 *
 * Two deliberate details:
 * <ul>
 *   <li>The app's own public origin (APP_BASE_URL) is always allowed, so the site's own pages are never
 *       rejected when a reverse proxy (Render) makes the request look like it came from a different scheme/port.</li>
 *   <li>The SSLCommerz callbacks (/api/payments/sslcommerz/**) get NO CORS configuration at all. The gateway's
 *       page sends the customer's browser there with a cross-origin form POST that carries
 *       "Origin: https://sandbox.sslcommerz.com"; with the /api/** rule that request was refused with
 *       "Invalid CORS request" before reaching the controller. These endpoints are plain navigations / a
 *       server-to-server IPN - no script ever reads their response - so CORS adds no protection there. Their
 *       real protection is server-side and unchanged: the SSLCommerz Validation API check of tran_id, amount and
 *       currency, atomic status transitions, val_id replay protection and the recovery scheduler (PaymentService).
 *       The rest of the API keeps its strict origin list (never "*").</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    /** Path prefix of the endpoints SSLCommerz calls (browser redirects + IPN). */
    static final String GATEWAY_CALLBACK_PREFIX = "/api/payments/sslcommerz/";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ORIGINS:http://localhost:8080,http://localhost:3000,http://localhost:5173}") String origins,
            @Value("${app.base-url:}") String appBaseUrl) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins(origins, appBaseUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource apiSource = new UrlBasedCorsConfigurationSource();
        apiSource.registerCorsConfiguration("/api/**", config);
        // No configuration (null) = Spring's CorsFilter lets the request through untouched.
        return request -> isGatewayCallback(request.getRequestURI(), request.getContextPath())
                ? null
                : apiSource.getCorsConfiguration(request);
    }

    /** True for the SSLCommerz success / fail / cancel / ipn callbacks. */
    static boolean isGatewayCallback(String requestUri, String contextPath) {
        if (requestUri == null) {
            return false;
        }
        String ctx = contextPath == null ? "" : contextPath;
        String path = requestUri.startsWith(ctx) ? requestUri.substring(ctx.length()) : requestUri;
        return path.startsWith(GATEWAY_CALLBACK_PREFIX) && !path.contains("..");
    }

    /** CORS_ORIGINS (comma separated) plus the origin of APP_BASE_URL, without duplicates or blanks. */
    static List<String> allowedOrigins(String corsOrigins, String appBaseUrl) {
        List<String> list = new ArrayList<>();
        if (corsOrigins != null) {
            Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(CorsConfig::stripTrailingSlash).filter(s -> !list.contains(s)).forEach(list::add);
        }
        String own = originOf(appBaseUrl);
        if (own != null && !list.contains(own)) {
            list.add(own);
        }
        return list;
    }

    /** "https://x.onrender.com/some/path" -> "https://x.onrender.com"; null when it is not an http(s) URL. */
    static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme() == null ? null : u.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme) || u.getHost() == null) {
                return null;
            }
            return scheme + "://" + u.getHost().toLowerCase(Locale.ROOT) + (u.getPort() == -1 ? "" : ":" + u.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
