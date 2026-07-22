package com.coresystem.coresystembackend.security;

import java.util.Date;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

	private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

	private final String jwtSecret;

	private final int jwtExpirationMs;

	public JwtUtils(@Value("${coresystem.app.jwtSecret}") String jwtSecret,
			@Value("${coresystem.app.jwtExpirationMs}") int jwtExpirationMs) {
		this.jwtSecret = jwtSecret;
		this.jwtExpirationMs = jwtExpirationMs;
	}

	private SecretKey key() {
		return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
	}

	public String generateTokenFromUname(String uname) {
		return Jwts.builder()
				.header().add("typ", "JWT").and()
				.subject(uname)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
				.signWith(key(), Jwts.SIG.HS512)
				.compact();
	}

	public String getUserNameFromJwt(String token) {
		return Jwts.parser()
				.verifyWith(key())
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
	}

	public boolean validateJwtToken(String authToken) {
		try {
			Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
			return true;
		} catch (JwtException e) {
			logger.error("Invalid JWT: {}", e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.error("JWT claims string is empty: {}", e.getMessage());
		}
		return false;
	}

}

// SDD-PROVENANCE: U-005 | vault: .mega-sdd/vaults/jwt-login | jjwt 0.12.x rewrite of newmojf JwtUtils (signWith(Key)+verifyWith)
