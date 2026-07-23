package com.coresystem.coresystembackend.service;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * LDAP UCS authentication service.
 *
 * <p>Replicates the behavior of newmojf {@code LDAP_UCS_Utils.authLDAPNew(uname, pass)}:
 * OAuth2 password-grant token fetch ({@code /token}) followed by a GET
 * {@code /ldap/verifypassword/userid/{uname}/password/{AES-ECB-hashed}} with an
 * HMAC-SHA512 {@code X-SIGNATURE}. The upstream response is mapped to
 * {@link LdapAuthResult} (responseCode / responseDescription) and passed through.
 *
 * <p>Adaptations from the reference (see SDD-PROVENANCE):
 * <ul>
 *   <li>Constructor injection of all config via {@code @Value} placeholders — no hardcoded
 *       URLs / credentials / AES key (constitution §D-002).</li>
 *   <li>Spring 7.x {@link RestClient} instead of raw {@code HttpURLConnection}.</li>
 *   <li>Jackson {@link ObjectMapper} instead of {@code org.json.simple} (not on classpath).</li>
 *   <li>NO {@code disableSslVerification()} trust-all — default JVM SSL is used
 *       (constitution §B-006).</li>
 *   <li>On exception, a GENERIC message is returned to the caller; the raw
 *       {@code e.getMessage()} is logged server-side only (constitution §B-007, OQ-FL-3).</li>
 * </ul>
 */
@Service
public class LdapUcsService {

	private static final Logger logger = LoggerFactory.getLogger(LdapUcsService.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private final RestClient restClient;
	private final String urlToken;
	private final String urlVerifyPassword;
	private final String clientId;
	private final String clientSecret;
	private final String username;
	private final String password;
	private final String partnerId;
	private final String channelId;
	private final String host;
	private final String aesKey;

	public LdapUcsService(
			@Value("${coresystem.ldap.urlToken}") String urlToken,
			@Value("${coresystem.ldap.urlVerifyPassword}") String urlVerifyPassword,
			@Value("${coresystem.ldap.clientId}") String clientId,
			@Value("${coresystem.ldap.clientSecret}") String clientSecret,
			@Value("${coresystem.ldap.username}") String username,
			@Value("${coresystem.ldap.password}") String password,
			@Value("${coresystem.ldap.partnerId}") String partnerId,
			@Value("${coresystem.ldap.channelId}") String channelId,
			@Value("${coresystem.ldap.host}") String host,
			@Value("${coresystem.ldap.aesKey}") String aesKey,
			RestClient.Builder restClientBuilder) {
		this.urlToken = urlToken;
		this.urlVerifyPassword = urlVerifyPassword;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.username = username;
		this.password = password;
		this.partnerId = partnerId;
		this.channelId = channelId;
		this.host = host;
		this.aesKey = aesKey;
		this.restClient = restClientBuilder.build();
	}

	/**
	 * Result of an LDAP UCS authentication attempt. Mirrors the upstream
	 * {@code {responseCode, responseDescription}} contract (OQ-FL-1). The service passes
	 * the upstream values through on success and returns a generic {@code 401} on internal
	 * exception (§B-007).
	 */
	public record LdapAuthResult(String responseCode, String responseDescription) {
	}

	/**
	 * Authenticate {@code uname} against LDAP UCS. Behavior replicates
	 * {@code LDAP_UCS_Utils.authLDAPNew}:
	 * <ol>
	 *   <li>Fetch an OAuth2 bearer token via {@link #getToken()}.</li>
	 *   <li>If the token is missing, return a generic token-error result.</li>
	 *   <li>Otherwise call {@link #getLdapProcess(String, String, String)} and pass through
	 *       its {@link LdapAuthResult}.</li>
	 *   <li>On any unexpected exception, return a generic {@code 401} result and log the
	 *       raw detail server-side (§B-007).</li>
	 * </ol>
	 */
	public LdapAuthResult authLDAPNew(String uname, String pass) {
		try {
			String token = getToken();
			if (token == null || token.isEmpty()) {
				// Token fetch failed (endpoint error / no access_token). Log raw detail
				// server-side; return a GENERIC authentication-failure result to the caller
				// so no internal detail (token endpoint state) leaks (§B-007).
				logger.error("LDAP auth failed: token was null/empty for user={}", uname);
				return new LdapAuthResult("401", "Authentication failed");
			}
			logger.info("LDAP UCS token acquired for user={}, calling verify-password", uname);
			LdapAuthResult ldapResult = getLdapProcess(uname, pass, token);
			logger.info("LDAP UCS verify-password result for user={}: responseCode={} responseDescription={}",
					uname, ldapResult.responseCode(), ldapResult.responseDescription());
			return ldapResult;
		} catch (Exception e) {
			// §B-007: log raw detail server-side, return GENERIC message to caller.
			logger.error("LDAP auth failed for user={}: {}", uname, e.getMessage(), e);
			return new LdapAuthResult("401", "Authentication failed");
		}
	}

	/**
	 * Fetch an OAuth2 access token (password grant) and return it as a
	 * {@code "Bearer <token>"} string. Returns {@code null} on any failure (the caller
	 * maps that to a generic token-error result).
	 */
	private String getToken() {
		String formBody = "grant_type=password"
				+ "&username=" + username
				+ "&password=" + password
				+ "&client_id=" + clientId
				+ "&client_secret=" + clientSecret;

		try {
			String responseBody = restClient.post()
					.uri(urlToken)
					.header("Host", host)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(formBody)
					.retrieve()
					.body(String.class);

			logger.debug("Token response received from LDAP UCS token endpoint");
			String accessToken = extractAccessToken(responseBody);
			return "Bearer " + accessToken;
		} catch (org.springframework.web.client.RestClientResponseException e) {
			// §B-007: log the upstream status + body SERVER-SIDE only for diagnosis;
			// the caller still gets a generic result, never this raw detail.
			logger.error("Failed to fetch LDAP UCS token: status={} body={}",
					e.getStatusCode(), e.getResponseBodyAsString(), e);
			return null;
		} catch (Exception e) {
			logger.error("Failed to fetch LDAP UCS token: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Call the verify-password endpoint and map the upstream JSON response into a
	 * {@link LdapAuthResult}. The password is AES/ECB-encrypted and the request is signed
	 * with HMAC-SHA512 (replicating {@code LDAP_UCS_Utils.getLdapProcess}).
	 */
	private LdapAuthResult getLdapProcess(String uname, String pass, String token) {
		try {
			String method = "GET";
			String hashedPassword = encryptPassword(pass);
			String url = urlVerifyPassword + "userid/" + uname + "/password/" + hashedPassword;

			String timestamp = generateIso8601Timestamp();
			String dataBody = "{}";
			String signature = calculateSignature(token, dataBody, url, method, timestamp);

			String refNum = String.valueOf(System.currentTimeMillis()).substring(0, 12);

			String responseBody = restClient.get()
					.uri(url)
					.header("Authorization", token)
					.header("X-SIGNATURE", signature)
					.header("X-TIMESTAMP", timestamp)
					.header("X-PARTNER-ID", partnerId)
					.header("X-EXTERNAL-ID", refNum)
					.header("CHANNEL-ID", channelId)
					.header("Content-Type", "application/json")
					.header("Host", host)
					.retrieve()
					.body(String.class);

			return parseLdapResponse(responseBody);
		} catch (Exception e) {
			// §B-007: log raw, return generic.
			logger.error("LDAP UCS verify-password call failed: {}", e.getMessage(), e);
			return new LdapAuthResult("401", "Authentication failed");
		}
	}

	/**
	 * Parse the upstream verify-password JSON into a {@link LdapAuthResult}. If the
	 * upstream body is missing the mapped fields, fall back to a generic failure rather
	 * than echoing raw content.
	 */
	private LdapAuthResult parseLdapResponse(String responseBody) {
		try {
			if (responseBody == null || responseBody.isBlank()) {
				logger.error("LDAP UCS verify-password returned an empty body");
				return new LdapAuthResult("401", "Authentication failed");
			}
			JsonNode root = objectMapper.readTree(responseBody);
			String responseCode = textOrNull(root, "responseCode");
			String responseDescription = textOrNull(root, "responseDescription");
			if (responseCode == null && responseDescription == null) {
				logger.error("LDAP UCS verify-password response missing responseCode/responseDescription");
				return new LdapAuthResult("401", "Authentication failed");
			}
			return new LdapAuthResult(
					responseCode != null ? responseCode : "401",
					responseDescription != null ? responseDescription : "Authentication failed");
		} catch (Exception e) {
			logger.error("Failed to parse LDAP UCS response: {}", e.getMessage(), e);
			return new LdapAuthResult("401", "Authentication failed");
		}
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode child = node.get(field);
		return (child != null && !child.isNull()) ? child.asText() : null;
	}

	private static String extractAccessToken(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode tokenNode = root.get("access_token");
			if (tokenNode == null || tokenNode.isNull()) {
				throw new IllegalStateException("access_token not found in token response");
			}
			return tokenNode.asText();
		} catch (Exception e) {
			throw new IllegalStateException("Error extracting access token", e);
		}
	}

	/**
	 * AES/ECB/PKCS5Padding-encrypt the password and Base64-encode the result. Replicates
	 * {@code LDAP_UCS_Utils.encryptPassword} (:267-279).
	 */
	private String encryptPassword(String password) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
			cipher.init(Cipher.ENCRYPT_MODE, keySpec);
			byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) {
			throw new RuntimeException("Error encrypting password", e);
		}
	}

	/**
	 * ISO-8601 timestamp for {@code X-TIMESTAMP}. Replicates
	 * {@code LDAP_UCS_Utils.generateIso8601Timestamp} (:281-284).
	 */
	private static String generateIso8601Timestamp() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		return sdf.format(new Date());
	}

	/**
	 * HMAC-SHA512 signature over {@code method:path:token:bodyHash:timestamp}, Base64-encoded.
	 * Replicates {@code LDAP_UCS_Utils.calculateSignature} (:286-319).
	 *
	 * @param bearer     the full {@code "Bearer <token>"} string
	 * @param body       the request body (always {@code "{}"} for this GET)
	 * @param url        the full request URL (only the path component is signed)
	 * @param method     the HTTP method (always {@code "GET"})
	 * @param timestamp  the {@code X-TIMESTAMP} value
	 */
	private String calculateSignature(String bearer, String body, String url, String method, String timestamp) {
		try {
			if (bearer == null || !bearer.contains(" ")) {
				throw new IllegalArgumentException("Invalid bearer token format");
			}
			String token = bearer.split(" ")[1];

			String bodyHash = String.format("%064x",
					new BigInteger(1,
							MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))))
					.toLowerCase();

			URI path = new URI(url);
			String pathString = path.getPath();

			String stringToSign = String.format("%s:%s:%s:%s:%s", method, pathString, token, bodyHash, timestamp);

			Mac mac = Mac.getInstance("HmacSHA512");
			SecretKeySpec secretKeySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
			mac.init(secretKeySpec);
			byte[] rawHmac = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

			return Base64.getEncoder().encodeToString(rawHmac);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

// SDD-PROVENANCE: U-007 | vault: .mega-sdd/vaults/jwt-login | replicates newmojf LDAP_UCS_Utils.authLDAPNew (RestClient, no disableSslVerification, generic error §B-007)
