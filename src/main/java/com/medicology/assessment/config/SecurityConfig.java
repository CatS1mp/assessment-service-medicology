package com.medicology.assessment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.exception.ErrorResponse;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8083}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeSecurityError(response, request.getRequestURI(), 401, 1401,
                                        resolveUnauthorizedMessage(authException)))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeSecurityError(response, request.getRequestURI(), 403, 1403,
                                        "You do not have permission to access this resource.")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                
                // 1. Cho phép các nguồn gửi yêu cầu
                configuration.setAllowedOrigins(Arrays.stream(corsAllowedOrigins.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList());
                
                // 2. Cho phép các phương thức HTTPS
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS","PATCH"));
                
                // 3. Cho phép tất cả các Header (Authorization, Content-Type, v.v.)
                configuration.setAllowedHeaders(Arrays.asList("*"));
                
                // 4. Cho phép gửi kèm Cookie/Auth Header
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

    private void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            String path,
            int status,
            int code,
            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(status, code, message, path, Instant.now());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String resolveUnauthorizedMessage(AuthenticationException authException) {
        String message = authException == null ? null : authException.getMessage();
        if (message == null || message.isBlank()) {
            return "Unauthorized: missing or invalid access token.";
        }
        return "Unauthorized: " + message;
    }
}
