package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Drive folder creation and template spreadsheet copies for the chart engine. */
@Component
public class DriveCopier {

    /**
     * Creates a Google Drive folder (shared-drive aware) to hold the per-report chart spreadsheet copies.
     *
     * @param drive authenticated Google Drive API client used to issue the create call
     * @param name display name for the new folder
     * @return the Drive file id of the newly created folder
     * @throws IOException if the Drive create request fails
     */
    public String createFolder(Drive drive, String name) throws IOException {
        com.google.api.services.drive.model.File folder = new com.google.api.services.drive.model.File()
            .setName(name)
            .setMimeType("application/vnd.google-apps.folder");
        return drive.files().create(folder).setFields("id").setSupportsAllDrives(true).execute().getId();
    }

    /**
     * Copies a chart-template spreadsheet, optionally placing the copy inside the given folder.
     *
     * @param drive authenticated Google Drive API client used to issue the copy call
     * @param fileId Drive file id of the source chart-template spreadsheet to duplicate
     * @param name display name for the resulting copy
     * @param folderId Drive folder id to set as the copy's parent; when null or empty the copy keeps the default location
     * @return the Drive file id of the newly created copy
     * @throws IOException if the Drive copy request fails
     */
    public String copyFile(Drive drive, String fileId, String name, String folderId) throws IOException {
        com.google.api.services.drive.model.File copy = new com.google.api.services.drive.model.File().setName(name);
        if (folderId != null && !folderId.isEmpty()) {
            copy.setParents(List.of(folderId));
        }
        return drive.files().copy(fileId, copy).setFields("id").setSupportsAllDrives(true).execute().getId();
    }
}
