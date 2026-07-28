package com.coresystem.coresystembackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Prod/dev security — permitAll for API testing (no JWT wired yet, OQ-ARCH-STACK).
 * When JWT auth lands, replace with real SecurityFilterChain.
 */
@Configuration
@Profile("prod")
public class ProdSecurityConfig {

    @Bean
    SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    org.springframework.web.client.RestClient.Builder restClientBuilder() {
        return org.springframework.web.client.RestClient.builder();
    }
}
// SDD-PROVENANCE: prod | vault: .mega-sdd/vaults/acquisition-master-data | ProdSecurityConfig — permitAll until JWT auth wired (OQ-ARCH-STACK)
