package it.bancaditalia.quarkus.platform.contract;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a key whose value is delivered from the vault at deploy time: never rendered from inventory,
 * never logged, never echoed by the conformance endpoint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Secret {
}
