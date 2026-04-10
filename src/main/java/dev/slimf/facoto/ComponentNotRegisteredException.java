package dev.slimf.facoto;

/**
 * Thrown when a requested component has no registration.
 * Mirrors Autofac's ComponentNotRegisteredException.
 */
public class ComponentNotRegisteredException extends DependencyResolutionException {

    private final Class<?> serviceType;
    private final String serviceName;

    public ComponentNotRegisteredException(Class<?> serviceType) {
        super("The requested service '" + serviceType.getName() + "' has not been registered.");
        this.serviceType = serviceType;
        this.serviceName = null;
    }

    public ComponentNotRegisteredException(String name, Class<?> serviceType) {
        super("The named service '" + name + "' of type '" + serviceType.getName() + "' has not been registered.");
        this.serviceType = serviceType;
        this.serviceName = name;
    }

    public Class<?> getServiceType() {
        return serviceType;
    }

    public String getServiceName() {
        return serviceName;
    }
}
