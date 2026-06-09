package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps Google data-source service records (connection status, sheet reads) to their V1 API DTOs.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface DataSourceApiMapper {

    /**
     * Converts the Google connection status into its V1 DTO.
     *
     * @param status the service connection status
     * @return the V1 connection status DTO
     */
    GoogleConnectionStatusV1 toStatus(GoogleConnectionStatus status);

    /**
     * Converts a successful sheet read into its V1 result DTO.
     *
     * @param data the service sheet data
     * @return the V1 sheet read result with {@code ok=true}
     */
    @Mapping(target = "ok", constant = "true")
    @Mapping(target = "error", ignore = true)
    SheetReadResultV1 toSuccess(SheetData data);

    /**
     * Builds the V1 result returned when a requested sheet tab is missing.
     *
     * @param tab the requested tab name
     * @return a V1 result with {@code ok=false} and error {@code "tab_not_found"}
     */
    default SheetReadResultV1 tabNotFound(String tab) {
        SheetReadResultV1 r = new SheetReadResultV1(false, tab, List.of(), 0, 0, List.of(), List.of(), List.of());
        r.setError("tab_not_found");
        return r;
    }
}
