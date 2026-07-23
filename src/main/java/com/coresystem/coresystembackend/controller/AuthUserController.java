package com.coresystem.coresystembackend.controller;

import com.coresystem.coresystembackend.dto.JwtResponse;
import com.coresystem.coresystembackend.dto.LoginRequest;
import com.coresystem.coresystembackend.dto.MessageResponse;
import com.coresystem.coresystembackend.entity.Users;
import com.coresystem.coresystembackend.repository.UserRepository;
import com.coresystem.coresystembackend.security.JwtUtils;
import com.coresystem.coresystembackend.service.LdapUcsService;
import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authentication controller exposing the login endpoint.
 *
 * <p>Wires LdapUcsService -> JwtUtils -> UserRepository -> JwtResponse.
 * Handles LDAP authentication result codes per the newmojf contract:
 * <ul>
 *   <li>"00" or "01" -> success, generate JWT, lookup user, return JwtResponse</li>
 *   <li>null result -> 400 "Failed to connect to LDAP service"</li>
 *   <li>other codes -> 400 with generic message (NOT raw responseDescription per OQ-FL-3)</li>
 *   <li>exception -> 401 "Authentication failed"</li>
 * </ul>
 *
 * <p>NO @CrossOrigin (CORS handled by SecurityConfig per OQ-AR-5).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthUserController {

	private static final Logger logger = LoggerFactory.getLogger(AuthUserController.class);

	private final LdapUcsService ldapUcsService;
	private final JwtUtils jwtUtils;
	private final UserRepository userRepository;

	public AuthUserController(LdapUcsService ldapUcsService, JwtUtils jwtUtils, UserRepository userRepository) {
		this.ldapUcsService = ldapUcsService;
		this.jwtUtils = jwtUtils;
		this.userRepository = userRepository;
	}

	@PostMapping("/dologin")
	public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
		try {
			LdapAuthResult result = ldapUcsService.authLDAPNew(loginRequest.getUname(), loginRequest.getPass());

			// Null result = connection failure
			if (result == null) {
				return ResponseEntity
						.status(HttpStatus.BAD_REQUEST)
						.body(new MessageResponse("Failed to connect to LDAP service"));
			}

			String responseCode = result.responseCode();

			// Success codes: "00" or "01"
			if ("00".equals(responseCode) || "01".equals(responseCode)) {
				String uname = loginRequest.getUname();
				String token = jwtUtils.generateTokenFromUname(uname);

				Optional<Users> userOpt = userRepository.findByUname(uname);
				if (userOpt.isEmpty()) {
					// User authenticated in LDAP but not found in local DB
					return ResponseEntity
							.status(HttpStatus.BAD_REQUEST)
							.body(new MessageResponse("User not found in local database"));
				}

				Users user = userOpt.get();
				String urole = "ROLE_" + user.getUrole();
				String mitKode = user.getKodeMitra() != null ? user.getKodeMitra() : "";

				JwtResponse jwtResponse = new JwtResponse(
						token,
						user.getId(),
						uname,
						mitKode,
						urole
				);

				return ResponseEntity.ok(jwtResponse);
			}

			// Non-success codes: return generic message, NOT raw responseDescription (OQ-FL-3)
			return ResponseEntity
					.status(HttpStatus.BAD_REQUEST)
					.body(new MessageResponse("Authentication failed"));

		} catch (Exception e) {
			// Exception during LDAP/JWT/DB call -> 401 with generic message (§B-007).
			// Log raw detail server-side for diagnosis; never expose to client.
			logger.error("Login failed for user={}: {}", loginRequest.getUname(), e.getMessage(), e);
			return ResponseEntity
					.status(HttpStatus.UNAUTHORIZED)
					.body(new MessageResponse("Authentication failed"));
		}
	}
}

// SDD-PROVENANCE: U-008 | vault: .mega-sdd/vaults/jwt-login | POST /api/auth/dologin wiring LdapUcsService+JwtUtils+UserRepository -> JwtResponse; application.yaml JWT+datasource+LDAP placeholders