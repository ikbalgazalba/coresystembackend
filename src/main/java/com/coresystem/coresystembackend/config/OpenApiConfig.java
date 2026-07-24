package com.coresystem.coresystembackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * OpenAPI 3 documentation configuration (binding C-008).
 *
 * <p>Exposes a single {@link OpenAPI} {@code @Bean} carrying the API metadata (title, description,
 * version) that springdoc-openapi merges with the endpoints it introspects from
 * {@code AuthUserController} and the DTOs ({@code LoginRequest}, {@code JwtResponse},
 * {@code MessageResponse}). The merged spec is served at {@code /v3/api-docs} and the UI at
 * {@code /swagger-ui.html}.
 *
 * <p>The version mirrors {@code pom.xml}'s {@code <version>} (0.0.1-SNAPSHOT). Prod Swagger-UI
 * gating is left as a seam — springdoc honors the {@code springdoc.swagger-ui.enabled} property,
 * which is set by the prod profile in U-005 (OQ-AP-5); no hard-disable is applied here.
 */
@Configuration
public class OpenApiConfig {

	/**
	 * API metadata bean merged into the generated OpenAPI 3 document.
	 *
	 * @return an {@link OpenAPI} with title, description and version for coresystembackend
	 */
	@Bean
	public OpenAPI coresystemOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("coresystembackend API")
						.description("Authentication + API surface for coresystembackend")
						.version("0.0.1-SNAPSHOT"));
	}

}

// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/api-platform | springdoc-openapi 3.0.3 + OpenApiConfig @Bean (OpenAPI 3 docs at /v3/api-docs + /swagger-ui.html)
