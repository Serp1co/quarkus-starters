package it.bancaditalia.quarkus.platform.contract;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Human-readable description of a configuration key. Exported into the config contract so the ops
 * audience reads what a key means without opening the code.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Doc {
    String value();
}
