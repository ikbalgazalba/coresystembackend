package com.coresystem.coresystembackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local/dev security — permitAll for API testing.
 * Only active under profile=local. NEVER use in production.
 */
@Configuration
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    org.springframework.web.client.RestClient.Builder restClientBuilder() {
        return org.springframework.web.client.RestClient.builder();
    }
}
// SDD-PROVENANCE: local-dev | vault: .mega-sdd/vaults/acquisition-master-data | LocalSecurityConfig — permitAll + RestClient.Builder for local profile
