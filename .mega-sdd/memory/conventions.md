# mega-sdd memory — conventions

Project: coresystembackend (Spring Boot 4.1.1-SNAPSHOT, Java 21, Maven)
Source: detected by mega-sdd:scan-codebase on 2026-07-22.

## Established conventions (status: established — re-verified each scan)

- **build_tool:** maven (wrapper committed; `./mvnw clean package` / `./mvnw test`)
- **java_version:** 21
- **spring_boot_version:** 4.1.1-SNAPSHOT (resolves from repo.spring.io/snapshot — network required)
- **config_format:** yaml (`src/main/resources/application.yaml`, NOT .properties)
- **base_package:** com.coresystem.coresystembackend
- **entry_point:** com.coresystem.coresystembackend.CoresystembackendApplication
- **test_suffix:** *Tests.java (Spring Initializr default; pack prefers *Test.java — pre-existing, not a conflict target)
- **layered_packages (target, not yet present):** controller / service / repository / entity / dto / config / security / exception
- **security_api:** Spring Security 7.x — SecurityFilterChain bean (WebSecurityConfigurerAdapter removed)
- **persistence_namespace:** jakarta.persistence (NOT javax.persistence — Boot 4.x mandate)

## Reference application (not the target repo)

- **newmojf** at /home/ikbalgazalba/AI/Project/newmojf is the IMPLEMENTATION PATTERN REFERENCE for this
  feature (JWT login via LDAP_UCS). It is NOT the rebuild target. Its conventions (javax.persistence,
  field @Autowired, model/ dir, .properties) are LEGACY and must be ADAPTED to the coresystembackend
  pack (jakarta.*, constructor injection, entity/ dir, yaml) when replicated — do not copy verbatim.
