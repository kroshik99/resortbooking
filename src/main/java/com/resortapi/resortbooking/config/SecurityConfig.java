package com.resortapi.resortbooking.config;

import tools.jackson.databind.ObjectMapper;
import com.resortapi.resortbooking.service.AppUserDetailsService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .securityMatcher("/api/**")
                // No browser sessions on the API, so there is no cookie for CSRF to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/availability", "/api/v1/room-types").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/room-types").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/room-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms").hasAnyRole("FRONT_DESK", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/rooms/*/status").hasAnyRole("FRONT_DESK", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/bookings/*/confirm",
                                "/api/v1/bookings/*/check-in",
                                "/api/v1/bookings/*/check-out").hasAnyRole("FRONT_DESK", "ADMIN")

                        // Reviewing and deciding requests is admin-only; filing one happens
                        // as a side effect of a front-desk check-in attempt, not its own call.
                        .requestMatchers("/api/v1/checkin-requests/**").hasRole("ADMIN")

                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> write(response,
                                HttpStatus.UNAUTHORIZED, "Not authenticated",
                                "A valid bearer token is required.", "UNAUTHENTICATED"))
                        .accessDeniedHandler((request, response, e) -> write(response,
                                HttpStatus.FORBIDDEN, "Not allowed",
                                "Your role cannot perform this action.", "ACCESS_DENIED")));

        return http.build();
    }

    /**
     * Browser pages: sessions and CSRF stay ON, unlike the stateless API above.
     * Order 2 means this only sees requests the /api/** matcher did not claim.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain pageSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // "/error" must be open: Spring forwards there on any failure, and a
                        // protected /error bounces anonymous visitors to the login page instead.
                        .requestMatchers("/", "/rooms", "/login", "/register", "/css/**", "/error").permitAll()
                        .requestMatchers("/staff/**").hasAnyRole("FRONT_DESK", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Every real page in the booking flow is named explicitly here...
                        .requestMatchers("/book", "/book/**", "/bookings", "/bookings/**", "/me/**")
                        .authenticated()
                        // ...so anyRequest() only ever catches a URL nothing maps to. Leaving
                        // that authenticated() would force even a typo'd URL through a login
                        // wall instead of a plain 404 - permitAll() here lets MVC 404 normally.
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        // false: send the guest back to the page they were trying to reach.
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    private void write(HttpServletResponse response, HttpStatus status,
                       String title, String detail, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
