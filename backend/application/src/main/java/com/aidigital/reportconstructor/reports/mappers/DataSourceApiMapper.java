package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = ApplicationMapperConfig.class)
public interface DataSourceApiMapper {

    GoogleConnectionStatusV1 toStatus(GoogleConnectionStatus status);

    @Mapping(target = "ok", constant = "true")
    @Mapping(target = "error", ignore = true)
    SheetReadResultV1 toSuccess(SheetData data);

    default SheetReadResultV1 tabNotFound(String tab) {
        SheetReadResultV1 r = new SheetReadResultV1(false, tab, List.of(), 0, 0, List.of(), List.of(), List.of());
        r.setError("tab_not_found");
        return r;
    }
}
