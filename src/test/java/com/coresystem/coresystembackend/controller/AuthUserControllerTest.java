package com.coresystem.coresystembackend.controller;

import com.coresystem.coresystembackend.dto.JwtResponse;
import com.coresystem.coresystembackend.dto.LoginRequest;
import com.coresystem.coresystembackend.dto.MessageResponse;
import com.coresystem.coresystembackend.entity.Users;
import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;
import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for AuthUserController login endpoint.
 *
 * <p>Pure unit test — no Spring context needed (Boot 4.1.1 does not ship
 * @WebMvcTest in spring-boot-test-autoconfigure; spring-security-test is
 * not on the test classpath). Controller is instantiated directly with
 * mocked collaborators.
 */
class AuthUserControllerTest {

    private LdapUcsService ldapUcsService;
    private JwtUtils jwtUtils;
    private UserRepository userRepository;
    private AuthUserController controller;

    @BeforeEach
    void setUp() {
        ldapUcsService = mock(LdapUcsService.class);
        jwtUtils = mock(JwtUtils.class);
        userRepository = mock(UserRepository.class);
        controller = new AuthUserController(ldapUcsService, jwtUtils, userRepository);
    }

    @Test
    void loginSuccess_returns200JwtResponse() {
        // Given: LDAP returns success code "00"
        LdapAuthResult successResult = new LdapAuthResult("00", "Success");
        when(ldapUcsService.authLDAPNew("testuser", "testpass")).thenReturn(successResult);
        when(jwtUtils.generateTokenFromUname("testuser")).thenReturn("mock-jwt-token");

        Users mockUser = new Users();
        mockUser.setId(42L);
        mockUser.setUname("testuser");
        mockUser.setKodeMitra("MITRA001");
        mockUser.setUrole(5L);
        when(userRepository.findByUname("testuser")).thenReturn(Optional.of(mockUser));

        LoginRequest req = new LoginRequest();
        req.setUname("testuser");
        req.setPass("testpass");

        // When
        ResponseEntity<?> response = controller.login(req);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JwtResponse body = (JwtResponse) response.getBody();
        assertEquals("mock-jwt-token", body.getToken());
        assertEquals(42L, body.getId());
        assertEquals("testuser", body.getUname());
        assertEquals("MITRA001", body.getMitKode());
        assertEquals("ROLE_5", body.getUrole());
    }

    @Test
    void ldapNull_returns400FailedToConnect() {
        // Given: LDAP service returns null (connection failure)
        when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUname("testuser");
        req.setPass("testpass");

        // When
        ResponseEntity<?> response = controller.login(req);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        MessageResponse body = (MessageResponse) response.getBody();
        assertEquals("Failed to connect to LDAP service", body.getMessage());
    }

    @Test
    void badCredentials_returns400AuthenticationFailed() {
        // Given: LDAP returns non-success code (e.g., "99")
        LdapAuthResult failResult = new LdapAuthResult("99", "Invalid credentials - internal detail");
        when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(failResult);

        LoginRequest req = new LoginRequest();
        req.setUname("testuser");
        req.setPass("wrongpass");

        // When
        ResponseEntity<?> response = controller.login(req);

        // Then: generic message, NOT raw responseDescription (OQ-FL-3)
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        MessageResponse body = (MessageResponse) response.getBody();
        assertEquals("Authentication failed", body.getMessage());
    }

    @Test
    void exception_returns401AuthenticationFailed() {
        // Given: LDAP service throws exception
        when(ldapUcsService.authLDAPNew(anyString(), anyString()))
                .thenThrow(new RuntimeException("LDAP connection error"));

        LoginRequest req = new LoginRequest();
        req.setUname("testuser");
        req.setPass("testpass");

        // When
        ResponseEntity<?> response = controller.login(req);

        // Then: 401 with generic message (§B-007)
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        MessageResponse body = (MessageResponse) response.getBody();
        assertEquals("Authentication failed", body.getMessage());
    }
}