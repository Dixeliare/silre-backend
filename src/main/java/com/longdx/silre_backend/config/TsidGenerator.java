package com.longdx.silre_backend.config;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Custom TSID generator annotation for Hibernate 7.2+
 * Replaces deprecated @GenericGenerator
 */
@IdGeneratorType(TsidIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
public @interface TsidGenerator {
}
