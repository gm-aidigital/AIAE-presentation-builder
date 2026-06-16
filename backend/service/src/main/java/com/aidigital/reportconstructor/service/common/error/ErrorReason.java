// ErrorReason — THE single enum for every business error code in this app.
//
// Naming convention: the enum constant IS the code (e.g. `C001`, not `NOT_FOUND`).
// This keeps the value seen in logs / API responses / error payloads identical
// to what you grep for in source — zero mental mapping.
//
//   throw new AppException(ErrorReason.C001, id);   // ← in code
//   { "code": "C001", "message": "Resource not found: 42" }   // ← in logs
//   grep -n 'ErrorReason\.C001' backend/   // ← to find every throw site
//
// Code format `<L>nnn`:
//   C — cross-cutting (UNEXPECTED, NOT_FOUND, FORBIDDEN, …)
//   <register new letters here, one per domain aggregate>
//
// Hard rules (enforced by SKILL + safety-review):
//   1. ONE `ErrorReason` enum in the project. Never per-domain enums
//      (`EmployeeErrorReason`, `OrderErrorReason`).
//   2. NO `AppErrorReason` interface — `AppException` takes `ErrorReason` directly.
//   3. New codes are added BY EDITING THIS FILE. PRs that add a new prefix letter
//      (e.g. introduce `E001` for an Employee aggregate) also update the letter
//      registry above. PRs that add a new code with an existing prefix do NOT
//      need to touch the registry.
//   4. If the new code's HTTP status mapping isn't already handled by an existing
//      prefix branch in GlobalExceptionHandler.statusForCode(), add a branch
//      there in the same change.

package com.aidigital.reportconstructor.service.common.error;

/**
 * Canonical registry of service-layer business error codes.
 */
public enum ErrorReason {

	C000("Unexpected error: %s"),
	C001("Resource not found: %s"),
	C002("Malformed request: %s"),
	C003("External call failed: %s"),
	C004("Access forbidden"),
	C005("Authentication required"),
	C006("Conflict: %s"),
	C007("Rate limit exceeded");

	private final String description;

	ErrorReason(String description) {
		this.description = description;
	}

	public String getCode() {
		return name();
	}

	public String getDescription() {
		return description;
	}
}
