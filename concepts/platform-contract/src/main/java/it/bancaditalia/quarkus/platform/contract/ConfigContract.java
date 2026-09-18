package it.bancaditalia.quarkus.platform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;

/**
 * The configuration contract of one application: every key the platform may or must render for it.
 * <p>
 * Derived by {@link ContractExporter} at build time from the application's {@code @ConfigMapping} interfaces
 * and messaging channels (what the code needs) plus the descriptors of the bdi-config-* modules on its
 * classpath (what the platform owns for the extensions in use). Nobody writes it: the platform's parent POM
 * generates {@code META-INF/config-contract.json} into every artifact, the deploy role validates the rendered
 * files against it, and the conformance endpoint resolves it at runtime.
 */
public final class ConfigContract {

    /** Who supplies the value at deploy time. */
    public enum Owner {
        /** Declared by the application through a {@code @ConfigMapping} interface. */
        APPLICATION,
        /** A key of a Quarkus extension in use; rendered by the platform from inventory or vault. */
        PLATFORM
    }

    public record Key(String name, String type, boolean required, Optional<String> defaultValue, boolean secret,
            Owner owner, String doc) {

        public Key {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(owner, "owner");
            doc = doc == null ? "" : doc;
        }

        /** Wildcard keys (maps, indexed lists) describe a family of keys and cannot be resolved individually. */
        public boolean pattern() {
            return name.contains("*");
        }
    }

    /** One contract key resolved against the live configuration; ordinal is the precedence of the winning source. */
    public record Echo(String key, boolean present, String value, String source, Integer ordinal, boolean required,
            boolean secret) {
    }

    public static final String MASK = "******";

    /** Prefix of the Quarkus keys that map an identity-provider group to application roles. */
    public static final String ROLES_MAPPING = "quarkus.http.auth.roles-mapping.";

    private final List<Key> keys;
    private final List<String> roles;

    private ConfigContract(List<Key> keys, List<String> roles) {
        this.keys = List.copyOf(keys);
        this.roles = List.copyOf(roles);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Reads a contract written by {@link #toJson()}. */
    public static ConfigContract fromJson(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            Builder builder = builder();
            for (JsonNode k : root.path("keys")) {
                builder.keys.add(new Key(k.path("name").asText(), k.path("type").asText(), k.path("required").asBoolean(),
                        k.path("default").isNull() ? Optional.empty() : Optional.of(k.path("default").asText()),
                        k.path("secret").asBoolean(), Owner.valueOf(k.path("owner").asText().toUpperCase(Locale.ROOT)),
                        k.path("doc").asText("")));
            }
            for (JsonNode r : root.path("roles")) {
                builder.role(r.asText());
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Key> keys() {
        return keys;
    }

    public Optional<Key> key(String name) {
        return keys.stream().filter(k -> k.name().equals(name)).findFirst();
    }

    /** The roles the application's code requires ({@code @RolesAllowed}), sorted. */
    public List<String> roles() {
        return roles;
    }

    /** Whether a security module in use expects the platform to map identity-provider groups to these roles. */
    public boolean rolesMappingExpected() {
        return !roles.isEmpty() && keys.stream().anyMatch(k -> k.name().equals(ROLES_MAPPING + "*"));
    }

    /**
     * For every role of the contract, the groups the running configuration maps to it (empty list: unmapped).
     * {@code quarkus.http.auth.roles-mapping.<group>=role1,role2} is the Quarkus key the platform renders.
     */
    public Map<String, List<String>> roleMappings(Config config) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        roles.forEach(r -> out.put(r, new ArrayList<>()));
        for (String property : config.getPropertyNames()) {
            if (!property.startsWith(ROLES_MAPPING)) {
                continue;
            }
            String group = property.substring(ROLES_MAPPING.length()).replace("\"", "");
            ConfigValue value = config.getConfigValue(property);
            if (value == null || value.getValue() == null) {
                continue;
            }
            for (String role : value.getValue().split(",")) {
                List<String> groups = out.get(role.trim());
                if (groups != null && !groups.contains(group)) {
                    groups.add(group);
                }
            }
        }
        out.values().forEach(java.util.Collections::sort); // property names come in no particular order
        return out;
    }

    /** Resolves every concrete key against the running configuration. Secret values are masked. */
    public List<Echo> echo(Config config) {
        List<Echo> out = new ArrayList<>();
        for (Key key : keys) {
            if (key.pattern()) {
                continue;
            }
            ConfigValue value = config.getConfigValue(key.name());
            boolean present = value != null && value.getValue() != null;
            out.add(new Echo(key.name(), present,
                    !present ? null : key.secret() ? MASK : value.getValue(),
                    present ? value.getSourceName() : null,
                    present ? value.getSourceOrdinal() : null,
                    key.required(), key.secret()));
        }
        return out;
    }

    /**
     * Names of the required keys the given configuration does not provide, and, when a security module expects a
     * roles mapping, {@code role:<name>} for every role no identity-provider group is mapped to.
     */
    public List<String> missing(Config config) {
        List<String> out = new ArrayList<>(echo(config).stream().filter(e -> e.required() && !e.present()).map(Echo::key).toList());
        if (rolesMappingExpected()) {
            roleMappings(config).forEach((role, groups) -> {
                if (groups.isEmpty()) {
                    out.add("role:" + role);
                }
            });
        }
        return out;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\n  \"keys\": [\n");
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            sb.append("    {\"name\": ").append(Json.str(k.name()))
                    .append(", \"type\": ").append(Json.str(k.type()))
                    .append(", \"required\": ").append(k.required())
                    .append(", \"default\": ").append(k.defaultValue().map(Json::str).orElse("null"))
                    .append(", \"secret\": ").append(k.secret())
                    .append(", \"owner\": ").append(Json.str(k.owner().name().toLowerCase(Locale.ROOT)))
                    .append(", \"doc\": ").append(Json.str(k.doc()))
                    .append('}').append(i < keys.size() - 1 ? "," : "").append('\n');
        }
        sb.append("  ],\n  \"roles\": [");
        for (int i = 0; i < roles.size(); i++) {
            sb.append(Json.str(roles.get(i))).append(i < roles.size() - 1 ? ", " : "");
        }
        return sb.append("]\n}\n").toString();
    }

    public String toMarkdownTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Key | Type | Required | Default | Owner | Description |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Key k : keys) {
            sb.append("| `").append(k.name()).append("` | ").append(k.type()).append(" | ")
                    .append(k.required() ? "yes" : "no").append(" | ")
                    .append(k.defaultValue().map(d -> "`" + d + "`").orElse("")).append(" | ")
                    .append(k.owner().name().toLowerCase(Locale.ROOT)).append(k.secret() ? " (secret)" : "")
                    .append(" | ").append(k.doc().replace("|", "\\|")).append(" |\n");
        }
        return sb.toString();
    }

    public static final class Builder {

        private final List<Key> keys = new ArrayList<>();
        private final List<String> roles = new ArrayList<>();

        /** Adds every key declared by a {@code @ConfigMapping} interface. */
        public Builder mapping(Class<?> configMapping) {
            keys.addAll(MappingIntrospector.introspect(configMapping));
            return this;
        }

        /** A platform-owned key without a default: the platform must render it. */
        public Builder platform(String name, String type, boolean required, String doc) {
            keys.add(new Key(name, type, required, Optional.empty(), false, Owner.PLATFORM, doc));
            return this;
        }

        /** A platform-owned key with a default: the platform may override it. */
        public Builder platform(String name, String type, String defaultValue, String doc) {
            keys.add(new Key(name, type, false, Optional.of(defaultValue), false, Owner.PLATFORM, doc));
            return this;
        }

        /** A platform-owned secret: required, delivered from the vault, masked everywhere. */
        public Builder platformSecret(String name, String doc) {
            keys.add(new Key(name, "String", true, Optional.empty(), true, Owner.PLATFORM, doc));
            return this;
        }

        /** Adds a key unless one with the same name exists (two modules may describe the same Quarkus key). */
        public Builder role(String role) {
            if (role != null && !role.isBlank() && !roles.contains(role.trim())) {
                roles.add(role.trim());
            }
            return this;
        }

        public Builder addIfAbsent(Key key) {
            if (keys.stream().noneMatch(k -> k.name().equals(key.name()))) {
                keys.add(key);
            }
            return this;
        }

        public ConfigContract build() {
            Set<String> seen = new HashSet<>();
            for (Key key : keys) {
                if (!seen.add(key.name())) {
                    throw new IllegalStateException("Duplicate contract key: " + key.name());
                }
            }
            List<String> sortedRoles = new ArrayList<>(roles);
            java.util.Collections.sort(sortedRoles);
            return new ConfigContract(keys, sortedRoles);
        }
    }
}
