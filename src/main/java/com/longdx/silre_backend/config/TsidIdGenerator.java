package com.longdx.silre_backend.config;

import com.github.f4b6a3.tsid.TsidFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TSID ID GENERATOR - Hibernate Integration
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Custom TSID generator for Hibernate 7.2 compatibility.
 * Uses tsid-creator library directly instead of hypersistence-utils.
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ ENTERPRISE-GRADE FEATURES │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ ✅ Fail-fast if factory not injected (prevents silent misconfiguration) │
 * │ ✅ No fallback to default factory (ensures proper initialization) │
 * │ ✅ Thread-safe via static factory injection │
 * │ ✅ Clear error messages for debugging │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ HOW IT WORKS │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ 1. TsidConfig creates TsidFactory with allocated Node ID during startup │
 * │ 2. TsidConfig injects factory via setTsidFactory() static method │
 * │ 3. When Hibernate saves an entity with @TsidGenerator: │
 * │ a) Hibernate calls generate(session, entity) │
 * │ b) generate() validates factory is injected (fail-fast) │
 * │ c) Calls tsidFactory.create().toLong() │
 * │ d) Returns generated TSID as Long (64-bit integer) │
 * │ 4. Hibernate sets this ID on the entity before INSERT │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ EXECUTION FLOW DIAGRAM │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ │
 * │ userRepository.save(newUser) │
 * │ │ │
 * │ ▼ │
 * │ Hibernate detects @TsidGenerator on @Id field │
 * │ │ │
 * │ ▼ │
 * │ TsidIdGenerator.generate(session, user) │
 * │ │ │
 * │ ▼ │
 * │ Validate: tsidFactory != null (fail-fast if null) │
 * │ │ │
 * │ ▼ │
 * │ tsidFactory.create() │
 * │ │ │
 * │ ▼ │
 * │ ┌────────────────────────┬───────────┬───────────────┐ │
 * │ │ Timestamp (42 bits) │ Node (10) │ Sequence (12) │ = TSID │
 * │ └────────────────────────┴───────────┴───────────────┘ │
 * │ │ │
 * │ ▼ │
 * │ .toLong() → Returns: 1234567890123456789L │
 * │ │ │
 * │ ▼ │
 * │ Hibernate sets user.internalId = 1234567890123456789L │
 * │ │ │
 * │ ▼ │
 * │ INSERT INTO users (internal_id, ...) VALUES (1234567890123456789, ...) │
 * │ │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ USAGE EXAMPLE │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ User user = new User(); │
 * │ user.setEmail("test@example.com"); │
 * │ // At this point, user.getInternalId() is null │
 * │ │
 * │ userRepository.save(user); │
 * │ // Hibernate calls TsidIdGenerator.generate() automatically │
 * │ // user.getInternalId() is now: 1234567890123456789L │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * @see TsidGenerator The annotation that triggers this generator
 * @see TsidConfig The configuration that creates and injects TsidFactory
 */
public class TsidIdGenerator implements IdentifierGenerator {

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Logger for this class - used to log factory injection and errors.
     */
    private static final Logger logger = LoggerFactory.getLogger(TsidIdGenerator.class);

    /**
     * Static factory shared across ALL instances of TsidIdGenerator.
     * 
     * WHY STATIC?
     * - Hibernate creates new TsidIdGenerator instance for each entity type
     * - We need ALL generators to use the SAME factory (same Node ID)
     * - Static ensures only ONE factory is used application-wide
     * 
     * INJECTED BY: TsidConfig.tsidFactory() during Spring Boot startup
     */
    private static TsidFactory tsidFactory;

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY INJECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Injects the TsidFactory instance from TsidConfig.
     * 
     * This method is called by {@link TsidConfig#tsidFactory()} during
     * Spring Boot application startup, BEFORE any entities are created.
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ WHY STATIC INJECTION? │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ • Hibernate creates TsidIdGenerator instances via reflection │
     * │ • We cannot use Spring @Autowired on Hibernate-managed classes │
     * │ • Static method allows TsidConfig to inject factory during startup │
     * │ • All generator instances share the SAME factory (same Node ID) │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * @param factory The TsidFactory configured with allocated Node ID
     */
    public static void setTsidFactory(TsidFactory factory) {
        tsidFactory = factory;
        logger.info("TSID Factory injected into TsidIdGenerator");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ID GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generates a new TSID for an entity.
     * 
     * Called by Hibernate when persisting an entity with {@code @TsidGenerator}
     * annotation on its {@code @Id} field.
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ FAIL-FAST BEHAVIOR │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ If tsidFactory is null, this method throws IllegalStateException │
     * │ instead of silently creating a default factory. │
     * │ │
     * │ WHY FAIL-FAST? │
     * │ • Prevents silent misconfiguration in production │
     * │ • Ensures proper Node ID allocation (prevents ID collisions) │
     * │ • Makes configuration errors obvious and debuggable │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * @param session Hibernate session (not used, but required by interface)
     * @param object  The entity being persisted (not used, but required by
     *                interface)
     * @return Generated TSID as a Long (64-bit integer)
     * @throws IllegalStateException if TsidFactory was not injected during startup
     */
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {

        // ─────────────────────────────────────────────────────────────────────
        // STEP 1: Validate factory is injected (FAIL-FAST)
        // ─────────────────────────────────────────────────────────────────────
        if (tsidFactory == null) {
            // FAIL-FAST: Don't silently create a default factory
            // This would cause ID collisions in multi-instance deployments
            // because all instances would use Node ID 0
            throw new IllegalStateException(
                    "TSID Factory not initialized. " +
                            "TsidConfig.tsidFactory() must be called during Spring context initialization. " +
                            "This indicates a configuration error - TSID cannot generate IDs without a configured factory.");
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 2: Generate TSID and return as Long
        // ─────────────────────────────────────────────────────────────────────
        // tsidFactory.create() → Creates TSID object with:
        // - Current timestamp (42 bits)
        // - Allocated Node ID (10 bits)
        // - Auto-incrementing sequence (12 bits)
        // .toLong() → Converts to 64-bit Long (e.g., 1234567890123456789L)
        return tsidFactory.create().toLong();
    }
}
