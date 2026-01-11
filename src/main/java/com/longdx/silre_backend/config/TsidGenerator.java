package com.longdx.silre_backend.config;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * ===============================================================================
 * TSID GENERATOR ANNOTATION
 * ===============================================================================
 * 
 * Custom annotation that tells Hibernate to use {@link TsidIdGenerator} for ID
 * generation.
 * This replaces the deprecated @GenericGenerator annotation (Hibernate 7.2+
 * compatible).
 * 
 * -------------------------------------------------------------------------------
 * WHAT IS TSID?
 * -------------------------------------------------------------------------------
 * TSID (Time-Sorted Unique Identifier) is a 64-bit integer ID that combines:
 * 
 * +------------------------+-----------+---------------------------+
 * | Timestamp (42 bits) | Node (10) | Sequence (12 bits) |
 * | ~139 years of IDs | 0-1023 | 0-4095 per ms |
 * +------------------------+-----------+---------------------------+
 * 
 * Example TSID: 1234567890123456789L
 * 
 * -------------------------------------------------------------------------------
 * WHY TSID?
 * -------------------------------------------------------------------------------
 * - Sortable : IDs naturally sort by creation time
 * - Unique : No collisions across multiple servers
 * - Fast : BIGINT is faster than UUID strings for database indexes
 * - Secure : Hard to guess (unlike sequential 1, 2, 3...)
 * - Compact : 64-bit vs UUID's 128-bit
 * 
 * -------------------------------------------------------------------------------
 * HOW IT WORKS
 * -------------------------------------------------------------------------------
 * 1. @IdGeneratorType links this annotation to TsidIdGenerator class
 * 2. @Retention(RUNTIME) keeps annotation available at runtime
 * 3. @Target({FIELD, METHOD}) allows use on fields or getter methods
 * 4. When Hibernate sees this on an @Id field, it calls TsidIdGenerator
 * 
 * -------------------------------------------------------------------------------
 * USAGE EXAMPLE
 * -------------------------------------------------------------------------------
 * 
 * @Entity
 *         public class User {
 * @Id
 * @TsidGenerator // <- Hibernate will call TsidIdGenerator.generate()
 * @Column(name = "internal_id")
 *              private Long internalId;
 *              }
 * 
 * @see TsidIdGenerator The actual generator implementation
 * @see TsidConfig The configuration that creates TsidFactory
 * @see TsidHealthIndicator Health monitoring for TSID system
 */
@IdGeneratorType(TsidIdGenerator.class) // Links this annotation to TsidIdGenerator class
@Retention(RetentionPolicy.RUNTIME) // Annotation is available at runtime (for Hibernate to read)
@Target({ FIELD, METHOD }) // Can be placed on fields or getter methods
public @interface TsidGenerator {
  // No parameters needed - all configuration is handled by TsidConfig
}
