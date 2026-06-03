package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Maps Google API failures on chart-template access into actionable deck warnings.
 */
@Component
public class ChartErrorTranslator {

    /**
     * @param tag chart pass label (e.g. {@code Tactic 3})
     * @param ex  underlying Google API failure
     * @return user-facing warning string
     */
    public String describeChartError(String tag, IOException ex) {
        if (ex instanceof GoogleJsonResponseException gjre) {
            int status = gjre.getStatusCode();
            String reason = null;
            GoogleJsonError details = gjre.getDetails();
            if (details != null && details.getErrors() != null && !details.getErrors().isEmpty()) {
                reason = details.getErrors().get(0).getReason();
            }
            String rawMessage = ex.getMessage();
            boolean permissionDenied = status == 403
                && (reason == null
                    || "insufficientFilePermissions".equals(reason)
                    || "forbidden".equals(reason)
                    || (rawMessage != null && rawMessage.toLowerCase(Locale.ROOT).contains("sufficient permissions")));
            if (permissionDenied) {
                return tag + ": chart template not accessible — sign in with a Google account that "
                    + "can open it, or re-share / re-home the template file so the running account "
                    + "has access (Google: " + rawMessage + ")";
            }
            boolean notFound = status == 404 || "notFound".equals(reason);
            if (notFound) {
                return tag + ": chart template not found — it may have been moved or deleted; "
                    + "re-share or re-create the template file (Google: " + rawMessage + ")";
            }
        }
        return tag + ": " + ex.getMessage();
    }
}
