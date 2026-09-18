package it.bancaditalia.quarkus.platform.contract;

/**
 * A piece of an application's config contract. The application contributes its {@code @ConfigMapping}
 * interfaces; each bdi-config-* module contributes the platform-owned keys of the extensions it configures.
 * Implementations are CDI beans; bdi-config-observability assembles them into the one {@link ConfigContract}.
 */
public interface ContractContributor {

    /** Order of the platform modules; applications keep the default so that their keys come first. */
    int PLATFORM_ORDER = 100;

    void contribute(ConfigContract.Builder builder);

    default int order() {
        return 0;
    }
}
