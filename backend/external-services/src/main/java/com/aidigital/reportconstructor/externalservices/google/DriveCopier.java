package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Drive folder creation and template spreadsheet copies for the chart engine. */
@Component
public class DriveCopier {

    /** Creates a Drive folder for chart copies; returns the new folder id. */
    public String createFolder(Drive drive, String name) throws IOException {
        com.google.api.services.drive.model.File folder = new com.google.api.services.drive.model.File()
            .setName(name)
            .setMimeType("application/vnd.google-apps.folder");
        return drive.files().create(folder).setFields("id").setSupportsAllDrives(true).execute().getId();
    }

    /** Copies a chart-template spreadsheet into {@code folderId} (optional). */
    public String copyFile(Drive drive, String fileId, String name, String folderId) throws IOException {
        com.google.api.services.drive.model.File copy = new com.google.api.services.drive.model.File().setName(name);
        if (folderId != null && !folderId.isEmpty()) {
            copy.setParents(List.of(folderId));
        }
        return drive.files().copy(fileId, copy).setFields("id").setSupportsAllDrives(true).execute().getId();
    }
}
