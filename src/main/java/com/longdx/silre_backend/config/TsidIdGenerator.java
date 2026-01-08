package com.longdx.silre_backend.config;

import com.github.f4b6a3.tsid.TsidFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

/**
 * Custom TSID generator for Hibernate 7.2 compatibility
 * Uses tsid-creator library directly instead of hypersistence-utils
 */
public class TsidIdGenerator implements IdentifierGenerator {

    private static TsidFactory tsidFactory;

    public static void setTsidFactory(TsidFactory factory) {
        tsidFactory = factory;
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        if (tsidFactory == null) {
            // Fallback: create a default factory if not injected
            tsidFactory = TsidFactory.builder().build();
        }
        return tsidFactory.create().toLong();
    }
}
