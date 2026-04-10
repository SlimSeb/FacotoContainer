package dev.slimf.facoto;

/**
 * Thrown when a dependency cannot be resolved.
 * Mirrors Autofac's DependencyResolutionException.
 */
public class DependencyResolutionException extends RuntimeException {

    public DependencyResolutionException(String message) {
        super(message);
    }

    public DependencyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
