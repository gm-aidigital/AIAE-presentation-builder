package com.aidigital.reportconstructor.admin.mappers;

import com.aidigital.reportconstructor.api.v1.model.AdminActiveUsersPeriodV1;
import com.aidigital.reportconstructor.api.v1.model.AdminFailedJobV1;
import com.aidigital.reportconstructor.api.v1.model.AdminSavingsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminRangeV1;
import com.aidigital.reportconstructor.api.v1.model.AdminStatsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTokenPeriodV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTokenLabelV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTokenTotalsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTotalsV1;
import com.aidigital.reportconstructor.api.v1.model.AdminTypeStatV1;
import com.aidigital.reportconstructor.api.v1.model.AdminUserStatV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.admin.dto.AdminActiveUsersPeriod;
import com.aidigital.reportconstructor.service.admin.dto.AdminFailedJob;
import com.aidigital.reportconstructor.service.admin.dto.AdminSavings;
import com.aidigital.reportconstructor.service.admin.dto.AdminRangeView;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenPeriod;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
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
	 * Converts the team-wide token consumption into its V1 DTO.
	 *
	 * @param tokenTotals the service token aggregation
	 * @return the V1 token totals DTO
	 */
	AdminTokenTotalsV1 toTokenTotals(AdminTokenTotals tokenTotals);

	/**
	 * Converts a per-stage spend row into its V1 DTO.
	 *
	 * @param tokenLabel the service per-stage spend row
	 * @return the V1 per-stage DTO
	 */
	AdminTokenLabelV1 toTokenLabel(AdminTokenLabel tokenLabel);

	/**
	 * Converts the reported window into its V1 DTO.
	 *
	 * @param range the window the figures cover
	 * @return the V1 range DTO
	 */
	AdminRangeV1 toRange(AdminRangeView range);

	/**
	 * Converts the modelled savings block into its V1 DTO.
	 *
	 * @param savings the service savings figure
	 * @return the V1 savings DTO
	 */
	AdminSavingsV1 toSavings(AdminSavings savings);

	/**
	 * Converts a token trend bucket into its V1 DTO.
	 *
	 * @param period the service trend bucket
	 * @return the V1 trend bucket DTO
	 */
	AdminTokenPeriodV1 toTokenPeriod(AdminTokenPeriod period);

	/**
	 * Converts an active-users bucket into its V1 DTO.
	 *
	 * @param period the service active-users bucket
	 * @return the V1 active-users DTO
	 */
	AdminActiveUsersPeriodV1 toActiveUsersPeriod(AdminActiveUsersPeriod period);

	/**
	 * Converts a failed-job row into its V1 DTO.
	 *
	 * @param failedJob the service failure row
	 * @return the V1 failed-job DTO
	 */
	AdminFailedJobV1 toFailedJob(AdminFailedJob failedJob);
}
