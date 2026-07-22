package com.coresystem.coresystembackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilsTest {

	private JwtUtils jwtUtils;

	private static final String TEST_SECRET = Base64.getEncoder().encodeToString(new byte[64]);

	@BeforeEach
	void setUp() {
		jwtUtils = new JwtUtils(TEST_SECRET, 900000);
		ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
		ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 900000);
	}

	@Test
	void generateTokenFromUname_returnsNonEmptyToken() {
		String token = jwtUtils.generateTokenFromUname("testuser");
		assertNotNull(token);
		assertTrue(token.length() > 0);
	}

	@Test
	void getUserNameFromJwt_returnsSubject() {
		String token = jwtUtils.generateTokenFromUname("testuser");
		assertEquals("testuser", jwtUtils.getUserNameFromJwt(token));
	}

	@Test
	void validateJwtToken_returnsTrueForValidToken() {
		String token = jwtUtils.generateTokenFromUname("testuser");
		assertTrue(jwtUtils.validateJwtToken(token));
	}

	@Test
	void validateJwtToken_returnsFalseForTamperedToken() {
		String token = jwtUtils.generateTokenFromUname("testuser");
		String tampered = token + "x";
		assertFalse(jwtUtils.validateJwtToken(tampered));
	}

}

// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/jwt-login | JwtUtils unit test (ReflectionTestUtils field injection)
