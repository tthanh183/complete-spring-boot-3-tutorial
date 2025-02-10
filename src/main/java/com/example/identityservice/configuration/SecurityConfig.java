package com.example.identityservice.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final String[] PUBLIC_ENDPOINTS = {
            "/users",
            "/auth/token",
            "/auth/introspect",
            "/auth/refresh",
            "/auth/logout",
    };

    @Value("${jwt.signerKey}")
    private String signerKey;


    // 3. Role-based authorization in Spring Security works as follows:
    //    - Spring Security differentiates between "roles" and "authorities":
    //      - A **role** is a high-level security designation (e.g., "ADMIN", "USER").
    //      - An **authority** is a more general permission, which can include roles (e.g., "SCOPE_ADMIN").
    //    - The `hasRole("ADMIN")` function automatically **adds the "ROLE_" prefix** before checking permissions.
    //      - `hasRole("ADMIN")` → Internally checks for `"ROLE_ADMIN"` authority.
    //      - `hasAuthority("SCOPE_ADMIN")` → Directly checks for `"SCOPE_ADMIN"` authority.
    // 4. Impact of different authority prefixes:
    //    - By default, Spring Security prefixes authorities extracted from the JWT **with "SCOPE_"**.
    //      - Example: If the JWT contains { "scope": "ADMIN USER" }, the granted authorities will be:
    //        ["SCOPE_ADMIN", "SCOPE_USER"].
    //      - This means:
    //        - ✅ `hasAuthority("SCOPE_ADMIN")` works correctly.
    //        - ❌ `hasRole("ADMIN")` will **fail**, because it checks for `"ROLE_ADMIN"`, not `"SCOPE_ADMIN"`.
    //
    //    - If we configure `setAuthorityPrefix("ROLE_")`, then authorities will be prefixed with `"ROLE_"` instead:
    //      - Example: { "scope": "ADMIN USER" } → Granted authorities: ["ROLE_ADMIN", "ROLE_USER"].
    //      - This ensures consistency with Spring Security’s role handling, so:
    //        - ✅ `hasAuthority("ROLE_ADMIN")` works correctly.
    //        - ✅ `hasRole("ADMIN")` works correctly, because it internally checks for `"ROLE_ADMIN"`.
    //
    //    - If we set `setAuthorityPrefix("")` (empty string), then authorities will be stored exactly as they appear in the JWT:
    //      - Example: { "scope": "ADMIN USER" } → Granted authorities: ["ADMIN", "USER"].
    //      - In this case:
    //        - ✅ `hasAuthority("ADMIN")` works correctly.
    //        - ❌ `hasRole("ADMIN")` **fails**, because `hasRole()` checks for `"ROLE_ADMIN"`, which doesn't exist.
    //
    //    - **Best Practice:**
    //      - **Use `setAuthorityPrefix("ROLE_")`** to ensure compatibility with `hasRole()`.
    //      - If roles in JWT already have a `"ROLE_"` prefix, set `setAuthorityPrefix("")` to prevent duplication (e.g., `"ROLE_ROLE_ADMIN"`).
    // 6. Why 401 Unauthorized errors are not handled by Global Exception Handler:
    //    - 401 errors occur **during authentication**, which happens at the filter level before reaching the controller/service.
    //    - Since global exception handlers work at the controller layer, they do not catch authentication failures.
    //    - To handle 401 errors, you must define an `AuthenticationEntryPoint` in Spring Security’s configuration.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .authorizeHttpRequests( request -> request.requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
//                        .requestMatchers(HttpMethod.GET, "/users").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated());
        httpSecurity.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));

        httpSecurity.csrf(AbstractHttpConfigurer::disable);
        return httpSecurity.build();
    }


    // 2. jwtGrantedAuthoritiesConverter provides methods to customize role mapping from JWT:
    //    - setAuthorityPrefix(String prefix): Changes the default "SCOPE_" prefix.
    //      - If set to "ROLE_", it ensures authorities align with Spring Security’s role convention.
    //      - If set to "", extracted values remain unchanged.
    //    - setAuthorityClaimDelimiter(String delimiter): Defines a custom delimiter for separating multiple roles.
    //      - Default is a space (" ").
    //    - setAuthorityClaimName(String claimName): Specifies a custom claim name (default is "scope") to extract roles.
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(), "HS512");
        return NimbusJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm(MacAlgorithm.HS512).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
