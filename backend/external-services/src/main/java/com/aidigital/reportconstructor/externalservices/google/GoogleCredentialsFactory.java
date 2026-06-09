package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
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

    /**
     * Eagerly loads the service-account credentials and trusted HTTP transport at
     * bean construction, failing fast with an {@link IllegalStateException} if the
     * configured JSON cannot be parsed into scoped {@link GoogleCredentials}.
     *
     * @param props external Google configuration holding the raw service-account
     *              JSON (from {@code GOOGLE_SERVICE_ACCOUNT_JSON}) used to build the credentials
     */
    public GoogleCredentialsFactory(GoogleProperties props) {
        try {
            this.transport = GoogleNetHttpTransport.newTrustedTransport();
            this.credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(
                    props.getServiceAccountJson().getBytes(StandardCharsets.UTF_8)))
                .createScoped(SCOPES);
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Failed to load Google service-account credentials from GOOGLE_SERVICE_ACCOUNT_JSON: "
                    + ex.getMessage(), ex);
        }
    }

    /**
     * Exposes the shared trusted HTTP transport so Sheets and Slides clients can
     * be built on a single connection layer.
     *
     * @return the trusted {@link HttpTransport} created once at construction
     */
    public HttpTransport transport() {
        return transport;
    }

    /**
     * Exposes the shared GSON-based JSON parser/serializer used when constructing
     * the Google API client builders.
     *
     * @return the default {@link GsonFactory} instance shared across Google clients
     */
    public GsonFactory jsonFactory() {
        return jsonFactory;
    }

    /**
     * Builds a request initializer that attaches the scoped service-account
     * credentials (as OAuth bearer tokens) to every outgoing Google API request.
     *
     * @return a fresh {@link HttpRequestInitializer} wrapping the loaded credentials
     */
    public HttpRequestInitializer initializer() {
        return new HttpCredentialsAdapter(credentials);
    }
}
