package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.slides.v1.Slides;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Builds Google API clients for the chart engine from shared transport/credentials.
 */
@Component
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class GoogleClientsFactory {

    private static final String APPLICATION_NAME = "Report Constructor — AI Digital";

    private final GoogleCredentialsFactory creds;

    public GoogleClientsFactory(GoogleCredentialsFactory creds) {
        this.creds = creds;
    }

    /** Builds a Drive client for the given credentials initializer. */
    public Drive drive(HttpRequestInitializer init) {
        return new Drive.Builder(creds.transport(), creds.jsonFactory(), init)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /** Builds a Sheets client for the given credentials initializer. */
    public Sheets sheets(HttpRequestInitializer init) {
        return new Sheets.Builder(creds.transport(), creds.jsonFactory(), init)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /** Builds a Slides client for the given credentials initializer. */
    public Slides slides(HttpRequestInitializer init) {
        return new Slides.Builder(creds.transport(), creds.jsonFactory(), init)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /** Service-account request initializer from {@link GoogleCredentialsFactory}. */
    public HttpRequestInitializer serviceAccountInitializer() {
        return creds.initializer();
    }

    /** OAuth access-token initializer for user-delegated chart builds. */
    public HttpRequestInitializer userInitializer(String accessToken) {
        return new HttpCredentialsAdapter(GoogleCredentials.create(new AccessToken(accessToken, null)));
    }
}
