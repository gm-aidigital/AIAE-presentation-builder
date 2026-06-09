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

    /**
     * Creates the factory backed by the shared HTTP transport and JSON factory.
     *
     * @param creds source of the shared Google transport, JSON factory, and service-account initializer
     */
    public GoogleClientsFactory(GoogleCredentialsFactory creds) {
        this.creds = creds;
    }

    /**
     * Builds a Drive client for the given credentials initializer.
     *
     * @param init request initializer carrying the credentials to authenticate Drive calls
     * @return a Drive client configured with the shared transport, JSON factory, and application name
     */
    public Drive drive(HttpRequestInitializer init) {
        return new Drive.Builder(creds.transport(), creds.jsonFactory(), init)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /**
     * Builds a Sheets client for the given credentials initializer.
     *
     * @param init request initializer carrying the credentials to authenticate Sheets calls
     * @return a Sheets client configured with the shared transport, JSON factory, and application name
     */
    public Sheets sheets(HttpRequestInitializer init) {
        return new Sheets.Builder(creds.transport(), creds.jsonFactory(), init)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /**
     * Builds a Slides client for the given credentials initializer.
     *
     * @param init request initializer carrying the credentials to authenticate Slides calls
     * @return a Slides client configured with the shared transport, JSON factory, and application name
     */
    public Slides slides(HttpRequestInitializer init) {
        return new Slides.Builder(creds.transport(), creds.jsonFactory(), init)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /**
     * Service-account request initializer from {@link GoogleCredentialsFactory}.
     *
     * @return the shared service-account initializer used for unattended chart builds
     */
    public HttpRequestInitializer serviceAccountInitializer() {
        return creds.initializer();
    }

    /**
     * OAuth access-token initializer for user-delegated chart builds.
     *
     * @param accessToken raw OAuth bearer token of the end user on whose behalf the chart is built
     * @return a request initializer that authenticates calls with the given user access token
     */
    public HttpRequestInitializer userInitializer(String accessToken) {
        return new HttpCredentialsAdapter(GoogleCredentials.create(new AccessToken(accessToken, null)));
    }
}
