package com.coresystem.coresystembackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.coresystem.coresystembackend.service.LdapUcsService.LdapAuthResult;

/**
 * Unit tests for {@link LdapUcsService}.
 *
 * <p>Uses {@link MockRestServiceServer} bound to the same {@link RestClient.Builder} the
 * service builds its {@link RestClient} from, so the service exercises its real HTTP code
 * path (token grant + verify-password GET) without ever hitting the network. No SSL bypass
 * is needed (and none exists in the service) because no real HTTPS connection is opened.
 */
class LdapUcsServiceTest {

	private static final String URL_TOKEN = "https://openapidev2.bankmega.local:15000/realms/quarkus/protocol/openid-connect/token";
	private static final String URL_VERIFY = "https://openapidev2.bankmega.local:15000/openapi/v1.0/ldap/verifypassword/";

	private static final String CLIENT_ID = "test-client-id";
	private static final String CLIENT_SECRET = "test-client-secret";
	private static final String USERNAME = "svc-user";
	private static final String PASSWORD = "svc-pass";
	private static final String PARTNER_ID = "test-partner";
	private static final String CHANNEL_ID = "test-channel";
	private static final String HOST = "openapidev2.bankmega.local";
	private static final String AES_KEY = "0123456789abcdef0123456789abcdef"; // 32 bytes -> AES-256 (test only)

	private RestClient.Builder restClientBuilder;
	private MockRestServiceServer mockServer;
	private LdapUcsService service;

	@BeforeEach
	void setUp() {
		restClientBuilder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
		service = new LdapUcsService(URL_TOKEN, URL_VERIFY, CLIENT_ID, CLIENT_SECRET, USERNAME,
				PASSWORD, PARTNER_ID, CHANNEL_ID, HOST, AES_KEY, restClientBuilder);
	}

	@Test
	void authLDAPNew_success_passesThroughUpstreamResponseCode() {
		mockServer.expect(requestTo(URL_TOKEN))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(
						"{\"access_token\":\"fake-token\",\"token_type\":\"Bearer\"}",
						MediaType.APPLICATION_JSON));

		mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(URL_VERIFY)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						"{\"responseCode\":\"00\",\"responseDescription\":\"Success\"}",
						MediaType.APPLICATION_JSON));

		LdapAuthResult result = service.authLDAPNew("someuser", "somepass");

		assertThat(result.responseCode()).isEqualTo("00");
		assertThat(result.responseDescription()).isEqualTo("Success");
		mockServer.verify();
	}

	@Test
	void authLDAPNew_badCredentials_passesThroughUpstreamFailureCode() {
		mockServer.expect(requestTo(URL_TOKEN))
				.andRespond(withSuccess(
						"{\"access_token\":\"fake-token\",\"token_type\":\"Bearer\"}",
						MediaType.APPLICATION_JSON));

		mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(URL_VERIFY)))
				.andRespond(withSuccess(
						"{\"responseCode\":\"99\",\"responseDescription\":\"Invalid username or password\"}",
						MediaType.APPLICATION_JSON));

		LdapAuthResult result = service.authLDAPNew("someuser", "wrongpass");

		// Service must pass through the upstream responseCode/description, not assume "00" is the only success.
		assertThat(result.responseCode()).isEqualTo("99");
		assertThat(result.responseDescription()).isEqualTo("Invalid username or password");
	}

	@Test
	void authLDAPNew_tokenEndpointError_returnsGeneric401AndDoesNotLeakRawException() {
		// Raw server-side exception text that must NEVER reach the client responseDescription (§B-007).
		final String rawSecretFragment = "openapidev2.bankmega.local";

		mockServer.expect(requestTo(URL_TOKEN))
				.andRespond(withServerError());

		LdapAuthResult result = service.authLDAPNew("someuser", "somepass");

		assertThat(result.responseCode()).isEqualTo("401");
		// Generic message — must NOT contain the raw exception text / endpoint detail.
		assertThat(result.responseDescription()).doesNotContain(rawSecretFragment);
		assertThat(result.responseDescription()).doesNotContain("Exception");
		assertThat(result.responseDescription()).doesNotContain("Connection");
	}

}
