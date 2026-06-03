package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Builds the shared {@link GoogleCredentials} + {@link HttpTransport} used by
 * {@link RealGoogleSheetsProvider} and {@link RealSlidesProvider}. Registered
 * only when {@code GOOGLE_SERVICE_ACCOUNT_JSON} is a non-blank env var; all
 * Google-backed beans inherit the same conditional through their constructor
 * dependency, so they auto-disappear together when creds are removed.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${external.google.service-account-json:}' != ''")
public class GoogleCredentialsFactory {

    // Full spreadsheets scope (not read-only): the chart engine writes pivoted
    // actuals into copied helper spreadsheets; read-only would block those writes.
    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/spreadsheets",
        "https://www.googleapis.com/auth/presentations",
        "https://www.googleapis.com/auth/drive"
    );

    private final GoogleCredentials credentials;
    private final HttpTransport transport;
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    public GoogleCredentialsFactory(GoogleProperties props) {
        try {
            this.transport = GoogleNetHttpTransport.newTrustedTransport();
            this.credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(
                    props.getServiceAccountJson().getBytes(StandardCharsets.UTF_8)))
                .createScoped(SCOPES);
            log.info("[google] service-account credentials loaded ({} scopes)", SCOPES.size());
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Failed to load Google service-account credentials from GOOGLE_SERVICE_ACCOUNT_JSON: "
                    + ex.getMessage(), ex);
        }
    }

    public HttpTransport transport() {
        return transport;
    }

    public GsonFactory jsonFactory() {
        return jsonFactory;
    }

    public HttpRequestInitializer initializer() {
        return new HttpCredentialsAdapter(credentials);
    }
}
