package com.coresystem.coresystembackend;

import com.coresystem.coresystembackend.entity.Users;
import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;
import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for login flow (F-U-001).
 *
 * <p>Uses @SpringBootTest with manual MockMvc setup (Boot 4.1.1 does not ship
 * @WebMvcTest or @AutoConfigureMockMvc). LDAP and DB are mocked via @MockitoBean
 * per OQ-AR-1/AR-2 (no real external dependencies).
 *
 * <p>DataSource auto-configuration is excluded to avoid requiring a real DB.
 */
@SpringBootTest
@Import({AuthLoginIntegrationTest.TestConfig.class})
class AuthLoginIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private LdapUcsService ldapUcsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();
    }

    @Test
    void loginSuccess_returnsJwtResponse() throws Exception {
        // Given: LDAP returns success code "00"
        LdapAuthResult successResult = new LdapAuthResult("00", "OK");
        when(ldapUcsService.authLDAPNew("alice", "x")).thenReturn(successResult);
        when(jwtUtils.generateTokenFromUname("alice")).thenReturn("test-jwt-token");

        Users mockUser = new Users();
        mockUser.setId(1L);
        mockUser.setUname("alice");
        mockUser.setKodeMitra("M01");
        mockUser.setUrole(2L);
        when(userRepository.findByUname("alice")).thenReturn(Optional.of(mockUser));

        // When & Then: POST /api/auth/dologin returns 200 with JwtResponse
        mockMvc.perform(post("/api/auth/dologin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uname\":\"alice\",\"pass\":\"x\"}"))
                .andExpect(status().isOk())
                // VERBATIM field names per ADV-001
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer "))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.uname").value("alice"))
                .andExpect(jsonPath("$.mitKode").value("M01"))
                .andExpect(jsonPath("$.urole").value("ROLE_2"));
    }

    @Test
    void loginBadCredentials_returns400() throws Exception {
        // Given: LDAP returns failure code "99"
        LdapAuthResult failResult = new LdapAuthResult("99", "Invalid credentials");
        when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(failResult);

        // When & Then: POST returns 400 with message
        mockMvc.perform(post("/api/auth/dologin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uname\":\"alice\",\"pass\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void loginLdapNull_returns400FailedToConnect() throws Exception {
        // Given: LDAP service returns null (connection failure)
        when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(null);

        // When & Then: POST returns 400 with specific message
        mockMvc.perform(post("/api/auth/dologin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uname\":\"alice\",\"pass\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Failed to connect to LDAP service"));
    }

    @Test
    void loginException_returns401() throws Exception {
        // Given: LDAP service throws exception
        when(ldapUcsService.authLDAPNew(anyString(), anyString()))
                .thenThrow(new RuntimeException("LDAP connection error - internal detail"));

        // When & Then: POST returns 401 with generic message
        // Per constitution §B-007: response body does NOT contain raw exception text
        mockMvc.perform(post("/api/auth/dologin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uname\":\"alice\",\"pass\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                // Guard: ensure no raw exception text leaked
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    /**
     * Test configuration to exclude DataSource auto-configuration.
     * Boot 4.1.1 moved these packages from spring-boot-autoconfigure to separate modules.
     */
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    static class TestConfig {
        // Empty config class for @ImportAutoConfiguration exclusion
    }
}