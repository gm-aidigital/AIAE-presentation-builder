package com.aidigital.reportconstructor.service.admin.enums;

/**
 * Granularity of a dashboard trend bucket.
 *
 * <p>Carries its own wire code rather than relying on {@code name()}, so renaming a constant cannot
 * silently change what the frontend receives.
 */
public enum AdminPeriodUnit {

	/** ISO week, running Monday to Sunday. */
	WEEK("week"),

	/** Calendar month. */
	MONTH("month");

	private final String code;

	AdminPeriodUnit(String code) {
		this.code = code;
	}

	/**
	 * Wire code of this granularity.
	 *
	 * @return the stable code sent to clients
	 */
	public String getCode() {
		return code;
	}
}
