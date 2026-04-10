package dev.slimf.facoto.internal;

import java.util.Objects;

/**
 * Identifies a service by type and optional name.
 * Used as a key in registration and resolution maps.
 */
public final class ServiceKey {

    private final Class<?> type;
    private final String name; // null = unnamed

    public ServiceKey(Class<?> type) {
        this(type, null);
    }

    public ServiceKey(Class<?> type, String name) {
        this.type = Objects.requireNonNull(type);
        this.name = name;
    }

    public Class<?> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public boolean isNamed() {
        return name != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceKey other)) return false;
        return type.equals(other.type) && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return name == null ? type.getName() : "'" + name + "' (" + type.getName() + ")";
    }
}
