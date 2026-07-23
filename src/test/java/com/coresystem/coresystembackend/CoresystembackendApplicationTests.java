package com.coresystem.coresystembackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;

// The app's production datasource is the remote newmojf UAT DB (OQ-AR-2) and LDAP UCS
// (OQ-AR-1), neither reachable/authenticated from the test environment without deploy-time
// env vars. JWT_SECRET is likewise externalized with no default (§D-002). This smoke test
// verifies the Spring context loads (component scan, bean wiring, SecurityConfig, controller,
// services) with the external-facing collaborators mocked, so no live JDBC or LDAP connection
// is attempted and no env-var secret needs to be present.
@SpringBootTest(properties = {
		// H2 not on the test classpath; point JPA at a no-op datasource and disable Hibernate's
		// metadata query so context load does not require a reachable DB.
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class CoresystembackendApplicationTests {

	@MockitoBean
	private LdapUcsService ldapUcsService;

	@MockitoBean
	private UserRepository userRepository;

	// JwtUtils @Value-injects ${coresystem.app.jwtSecret}, which has no default (§D-002).
	// Mocking it avoids resolving JWT_SECRET during the context-load smoke test.
	@MockitoBean
	private JwtUtils jwtUtils;

	@Test
	void contextLoads() {
	}

}
