package me.nithanim.quarkus.azure.genericfunction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a generated quarkus entry point such that it can be ignored from transformation. This is
 * intended for the case when a transformation was applied but is (for whatever reason) applied
 * again. Not sure if it is ever needed, but it does not hurt.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface QuarkusAzureFunctionEntry {}
