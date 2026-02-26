package de.devin.cbbees.util

/**
 * Marks a function, property, or class that should only be used on the server side.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ServerSide

/**
 * Marks a function, property, or class that should only be used on the client side.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ClientSide