package com.aidigital.reportconstructor.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on Spring's caching abstraction for the application.
 *
 * <p>Deliberately brings no cache provider with it. With none on the classpath Spring Boot supplies
 * a simple in-memory manager, which is the right size for what is actually cached here: one admin
 * dashboard snapshot per instance, invalidated by the thing that makes it stale rather than by a
 * timer. A distributed cache would add a dependency and an operational surface to hold a few
 * kilobytes that can always be recomputed.
 *
 * <p>This is the application-level cache configuration the database rules call for — cache setup
 * lives here rather than being scattered across services.
 */
@Configuration
@EnableCaching
public class CacheConfig {

}
