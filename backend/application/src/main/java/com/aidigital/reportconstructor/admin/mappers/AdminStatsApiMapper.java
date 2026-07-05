package com.aidigital.reportconstructor.admin.mappers;

import com.aidigital.reportconstructor.api.v1.model.AdminDayVolumeV1;
import com.aidigital.reportconstructor.api.v1.model.AdminStatsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTotalsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTypeStatV1;
import com.aidigital.reportconstructor.api.v1.model.AdminUserStatV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.admin.dto.AdminDayVolume;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.dto.AdminTotals;
import com.aidigital.reportconstructor.service.admin.dto.AdminTypeStat;
import com.aidigital.reportconstructor.service.admin.dto.AdminUserStat;
import org.mapstruct.Mapper;

/**
 * Maps the service-layer admin aggregation records to their V1 API DTOs.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface AdminStatsApiMapper {

	/**
	 * Converts the aggregated admin stats into the V1 dashboard payload.
	 *
	 * @param stats the service aggregation
	 * @return the V1 admin stats DTO
	 */
	AdminStatsV1 toStats(AdminStats stats);

	/**
	 * Converts the headline totals into their V1 DTO.
	 *
	 * @param totals the service totals
	 * @return the V1 totals DTO
	 */
	AdminTotalsV1 toTotals(AdminTotals totals);

	/**
	 * Converts a per-user row into its V1 DTO.
	 *
	 * @param userStat the service per-user row
	 * @return the V1 per-user DTO
	 */
	AdminUserStatV1 toUserStat(AdminUserStat userStat);

	/**
	 * Converts a per-type count into its V1 DTO.
	 *
	 * @param typeStat the service per-type count
	 * @return the V1 per-type DTO
	 */
	AdminTypeStatV1 toTypeStat(AdminTypeStat typeStat);

	/**
	 * Converts a daily volume point into its V1 DTO.
	 *
	 * @param dayVolume the service daily volume point
	 * @return the V1 daily volume DTO
	 */
	AdminDayVolumeV1 toDayVolume(AdminDayVolume dayVolume);
}
