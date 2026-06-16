package com.aidigital.reportconstructor.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct policy for API-layer DTO mappers.
 */
@MapperConfig(
		componentModel = MappingConstants.ComponentModel.SPRING,
		unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ApplicationMapperConfig {

}
