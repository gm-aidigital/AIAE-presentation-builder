package com.aidigital.reportconstructor.externalservices.clerk;

import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a signed-in user's Google OAuth access token from the Clerk Backend
 * API. Used so source spreadsheets can be read with the caller's own Google
 * account (as well as so generated decks can be created in the user's Drive).
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.clerk.secret-key:}' != ''")
public class ClerkOAuthTokenService implements UserGoogleTokenProvider {

	private static final String API_BASE = "https://api.clerk.com/v1";

	private final String secretKey;
	private final String provider;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ObjectMapper mapper = new ObjectMapper();

	public ClerkOAuthTokenService(ClerkProperties props) {
		this.secretKey = props.getSecretKey();
		this.provider = props.getGoogleProvider();
	}

	@Override
	public String googleAccessToken(String clerkUserId) {
		if (clerkUserId == null || clerkUserId.isBlank()) {
			return null;
		}
		try {
			HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create(API_BASE + "/users/" + clerkUserId
							+ "/oauth_access_tokens/" + provider))
					.header("Authorization", "Bearer " + secretKey)
					.header("Accept", "application/json")
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() / 100 != 2) {
				log.warn("[clerk] Google token fetch for user {} returned HTTP {}",
						clerkUserId, resp.statusCode());
				return null;
			}
			JsonNode root = mapper.readTree(resp.body());
			JsonNode arr = root.isArray() ? root : root.path("data");
			if (arr.isArray() && !arr.isEmpty()) {
				String token = arr.get(0).path("token").asText(null);
				if (token != null && !token.isBlank()) {
					return token;
				}
			}
			log.warn("[clerk] no Google OAuth token present for user {}", clerkUserId);
			return null;
		} catch (Exception ex) {
			log.warn("[clerk] Google token fetch failed for user {}: {}",
					clerkUserId, ex.getMessage());
			return null;
		}
	}
}
