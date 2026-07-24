# mega-sdd memory — conventions

Project: coresystembackend (Spring Boot 4.1.1-SNAPSHOT, Java 21, Maven)
Source: detected by mega-sdd:scan-codebase on 2026-07-24 (refresh to HEAD 0682bb5; jwt-login vault now landed in source).

## Established conventions (status: established — re-verified each scan)

- **build_tool:** maven (wrapper committed; `./mvnw clean package` / `./mvnw test` / `./mvnw spring-boot:run`)
- **java_version:** 21
- **spring_boot_version:** 4.1.1-SNAPSHOT (resolves from repo.spring.io/snapshot — network required; behavior may shift between snapshot resolutions)
- **config_format:** yaml (`src/main/resources/application.yaml`, NOT .properties)
- **base_package:** com.coresystem.coresystembackend
- **entry_point:** com.coresystem.coresystembackend.CoresystembackendApplication
- **test_suffix:** mixed — `*Test.java` (unit, pack-preferred), `*Tests.java` (smoke `CoresystembackendApplicationTests`), `*IntegrationTest.java` (integration). All established.
- **layered_packages (PRESENT, feature-bearing):** controller / service / repository / entity / dto / config / security. (No `exception/` package yet — no @RestControllerAdvice/@ControllerAdvice present.)
- **security_api:** Spring Security 7.x — SecurityFilterChain bean (WebSecurityConfigurerAdapter removed). Stateless JWT, CSRF disabled, permitAll /api/auth/**.
- **persistence_namespace:** jakarta.persistence (NOT javax.persistence — Boot 4.x mandate). JPA ddl-auto=none (schema pre-exists in newmojf DB).
- **secrets:** env-externalized (constitution §D-002). application.yaml uses ${VAR} placeholders with NO defaults (fail-fast). .env (gitignored) + .env.example (contract, 15 vars) + run-app.sh (auto-load + fail-fast).
- **http_client:** Spring 7.x RestClient (NOT RestTemplate/WebClient). RestClient.Builder bean provided explicitly in SecurityConfig (Boot 4.x removed the auto-config).
- **jwt_lib:** jjwt 0.12.6 (HS512). generateTokenFromUname / getUserNameFromJwt / validateJwtToken in JwtUtils @Component.
- **provenance_markers:** source files carry `// SDD-PROVENANCE: U-NNN | vault: .mega-sdd/vaults/jwt-login | <summary>` trailing comments.

## Known gaps detected this scan (re-verify next scan; surface as OQs where relevant)

- **JWT bearer filter ABSENT:** JwtUtils.validateJwtToken exists but is NOT wired into the SecurityFilterChain (no OncePerRequestFilter). `/dologin` issues tokens; no filter validates them on protected routes. anyRequest().authenticated() has no authentication source wired.
- **Role not enforced:** Users.urole is emitted as a JWT response claim (prefixed ROLE_) but no hasRole matcher / GrantedAuthority / UserDetailsService enforces it as authorization.
- **No input validation:** @Valid/@Validated not used on @RequestBody LoginRequest (no Bean Validation layer).
- **No global exception handling:** no @RestControllerAdvice / @ControllerAdvice.
- **Host-specific trust store:** run-app.sh hardcodes /home/ikbalgazalba/.ssl-truststores/bankmega-truststore.p12 — NOT portable into a container (relevant to containerization epic).

## Reference application (not the target repo)

- **newmojf** at /home/ikbalgazalba/AI/Project/newmojf is the IMPLEMENTATION PATTERN REFERENCE for the
  jwt-login feature (JWT login via LDAP_UCS). It is NOT the rebuild target. Its conventions (javax.persistence,
  field @Autowired, model/ dir, .properties) are LEGACY and must be ADAPTED to the coresystembackend
  pack (jakarta.*, constructor injection, entity/ dir, yaml) when replicated — do not copy verbatim.
