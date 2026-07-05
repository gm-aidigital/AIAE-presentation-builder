package com.aidigital.reportconstructor.service.admin.dto;

/**
 * Report count for a single report type, for the "By report type" bars.
 *
 * @param type  uppercased report type code, or {@code OTHER} when unknown
 * @param count number of reports of this type
 */
public record AdminTypeStat(String type, int count) {
}
