package com.coresystem.coresystembackend.controller;

import com.coresystem.coresystembackend.dto.JwtResponse;
import com.coresystem.coresystembackend.dto.LoginRequest;
import com.coresystem.coresystembackend.entity.Users;
import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;
import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for AuthUserController login endpoint.
 *
 * <p>Uses @WebMvcTest slice with @MockBean for services.
 * Security filters are automatically added; for unit testing the controller logic,
 * we test the raw controller behavior.
 */
@WebMvcTest(AuthUserController.class)
class AuthUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private LdapUcsService ldapUcsService;

	@MockitoBean
	private JwtUtils jwtUtils;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void loginSuccess_returns200JwtResponse() throws Exception {
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

		LoginRequest request = new LoginRequest();
		request.setUname("testuser");
		request.setPass("testpass");

		// When & Then: POST /api/auth/dologin returns 200 with JwtResponse
		mockMvc.perform(post("/api/auth/dologin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("mock-jwt-token")))
				.andExpect(content().string(containsString("testuser")))
				.andExpect(content().string(containsString("MITRA001")))
				.andExpect(content().string(containsString("ROLE_5")));
	}

	@Test
	void ldapNull_returns400FailedToConnect() throws Exception {
		// Given: LDAP service returns null (connection failure)
		when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(null);

		LoginRequest request = new LoginRequest();
		request.setUname("testuser");
		request.setPass("testpass");

		// When & Then: POST /api/auth/dologin returns 400 with message
		mockMvc.perform(post("/api/auth/dologin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("Failed to connect to LDAP service")));
	}

	@Test
	void badCredentials_returns400AuthenticationFailed() throws Exception {
		// Given: LDAP returns non-success code (e.g., "99" for invalid credentials)
		LdapAuthResult failResult = new LdapAuthResult("99", "Invalid credentials - internal detail");
		when(ldapUcsService.authLDAPNew(anyString(), anyString())).thenReturn(failResult);

		LoginRequest request = new LoginRequest();
		request.setUname("testuser");
		request.setPass("wrongpass");

		// When & Then: POST /api/auth/dologin returns 400 with GENERIC message (not raw description)
		mockMvc.perform(post("/api/auth/dologin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("Authentication failed")));
	}

	@Test
	void exception_returns401AuthenticationFailed() throws Exception {
		// Given: LDAP service throws exception
		when(ldapUcsService.authLDAPNew(anyString(), anyString()))
				.thenThrow(new RuntimeException("LDAP connection error"));

		LoginRequest request = new LoginRequest();
		request.setUname("testuser");
		request.setPass("testpass");

		// When & Then: POST /api/auth/dologin returns 401 with generic message
		mockMvc.perform(post("/api/auth/dologin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string(containsString("Authentication failed")));
	}
}