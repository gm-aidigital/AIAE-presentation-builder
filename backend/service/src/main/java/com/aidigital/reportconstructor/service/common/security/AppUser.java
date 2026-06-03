package com.aidigital.reportconstructor.service.common.security;

/**
 * Immutable caller context passed through the service layer.
 * Built by {@code AppUserFactory} from the Clerk JWT.
 *
 * <p>This is the baseline identity record. It carries only the three claims
 * that Clerk's {@code aidigital-api} JWT template always provides. Roles are
 * an opt-in extension — if the product needs role-based authorization, add a
 * separate {@code roles} field here keyed by the stable {@code userId}.
 *
 * @param userId    Stable Clerk user id — the {@code user_id} claim (equals {@code sub}).
 * @param email     Lowercased canonical email from the {@code email} claim.
 * @param fullName  Display name from the {@code full_name} claim, falling back to email.
 */
public record AppUser(String userId, String email, String fullName) { }
