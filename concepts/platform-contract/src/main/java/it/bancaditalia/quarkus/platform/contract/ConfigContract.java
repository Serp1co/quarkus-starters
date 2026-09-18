package it.bancaditalia.quarkus.platform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;

/**
 * The developer/platform configuration contract of design notes §2.1 and §2.2: every key an application
 * reads, who owns it, its phase (fixed in the artifact at build time, or rendered by the platform at runtime),
 * its type and constraints, whether it is secret, and the roles the code names. Derived at build time by
 * {@link ContractExporter}, checked by the Ansible pre-deploy validation, echoed at runtime by the
 * conformance endpoint ({@link #echo}, {@link #missing}, {@link #violations}).
 */
public final class ConfigContract {

    public enum Owner {
        /** Declared by the application through a {@code @ConfigMapping} interface. */
        APPLICATION,
        /** A key of a Quarkus extension in use; rendered by the platform from inventory or vault. */
        PLATFORM
    }

    /** When a key takes effect: rendering a build-time key at deploy time changes nothing and is refused. */
    public enum Phase {
        /** Read when the application starts: the platform renders it per environment. */
        RUNTIME,
        /** Fixed in the artifact when it is built: changing it means rebuilding with another starter or default. */
        BUILD_TIME;

        public String json() {
            return this == BUILD_TIME ? "build-time" : "runtime";
        }

        public static Phase parse(String text) {
            return text != null && text.replace('_', '-').equalsIgnoreCase("build-time") ? BUILD_TIME : RUNTIME;
        }
    }

    /** What a value must satisfy, beyond its type; {@code requiredIf} is {@code other.key=value}. */
    public record Constraints(Optional<String> min, Optional<String> max, Optional<String> pattern, List<String> values,
            Optional<String> requiredIf) {

        public static final Constraints NONE = new Constraints(Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                Optional.empty());

        public Constraints {
            Objects.requireNonNull(min);
            Objects.requireNonNull(max);
            Objects.requireNonNull(pattern);
            values = values == null ? List.of() : List.copyOf(values);
            Objects.requireNonNull(requiredIf);
        }

        public boolean isEmpty() {
            return min.isEmpty() && max.isEmpty() && pattern.isEmpty() && values.isEmpty() && requiredIf.isEmpty();
        }
    }

    public record Key(String name, String type, boolean required, Optional<String> defaultValue, boolean secret,
            Owner owner, String doc, Phase phase, Constraints constraints) {

        public Key {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(owner, "owner");
            doc = doc == null ? "" : doc;
            phase = phase == null ? Phase.RUNTIME : phase;
            constraints = constraints == null ? Constraints.NONE : constraints;
        }

        /** A runtime key without constraints. */
        public Key(String name, String type, boolean required, Optional<String> defaultValue, boolean secret, Owner owner,
                String doc) {
            this(name, type, required, defaultValue, secret, owner, doc, Phase.RUNTIME, Constraints.NONE);
        }

        public Key withPhase(Phase newPhase) {
            return new Key(name, type, required, defaultValue, secret, owner, doc, newPhase, constraints);
        }

        public Key withConstraints(Constraints newConstraints) {
            return new Key(name, type, required, defaultValue, secret, owner, doc, phase, newConstraints);
        }

        /** Wildcard keys (maps, indexed lists, named resources) describe a family of keys and cannot be resolved individually. */
        public boolean pattern() {
            return name.contains("*");
        }

        /** Matches a concrete key against this key's name, {@code *} standing for one segment (quoted or not). */
        public boolean matches(String concreteKey) {
            if (!pattern()) {
                return name.equals(concreteKey);
            }
            return Pattern.compile("^" + Pattern.quote(name).replace("*", "\\E(\"[^\"]*\"|[^.]+)\\Q") + "$")
                    .matcher(concreteKey).matches();
        }

        public boolean buildTime() {
            return phase == Phase.BUILD_TIME;
        }
    }

    /** One contract key resolved against the live configuration; ordinal is the precedence of the winning source. */
    public record Echo(String key, boolean present, String value, String source, Integer ordinal, boolean required,
            boolean secret, String phase) {
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
                builder.keys.add(keyFromJson(k, Owner.valueOf(k.path("owner").asText("platform").toUpperCase(Locale.ROOT))));
            }
            for (JsonNode r : root.path("roles")) {
                builder.role(r.asText());
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One key from its JSON form; shared with the platform descriptors, whose keys carry no owner. */
    static Key keyFromJson(JsonNode k, Owner owner) {
        List<String> values = new ArrayList<>();
        k.path("values").forEach(v -> values.add(v.asText()));
        Constraints constraints = new Constraints(text(k, "min"), text(k, "max"), text(k, "pattern"), values, text(k, "required-if"));
        return new Key(k.path("name").asText(), k.path("type").asText("String"), k.path("required").asBoolean(false),
                text(k, "default"), k.path("secret").asBoolean(false), owner, k.path("doc").asText(""),
                Phase.parse(k.path("phase").asText(null)), constraints);
    }

    private static Optional<String> text(JsonNode node, String field) {
        return node.hasNonNull(field) ? Optional.of(node.path(field).asText()) : Optional.empty();
    }

    public List<Key> keys() {
        return keys;
    }

    public Optional<Key> key(String name) {
        return keys.stream().filter(k -> k.name().equals(name)).findFirst();
    }

    /** The contract key a concrete key belongs to: itself, or the wildcard family it matches. */
    public Optional<Key> match(String concreteKey) {
        return keys.stream().filter(k -> k.matches(concreteKey)).findFirst();
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
        out.values().forEach(Collections::sort); // property names come in no particular order
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
                    isRequired(key, config), key.secret(), key.phase().json()));
        }
        return out;
    }

    /** Required outright, or required because the key named in {@code required-if} has the given value. */
    public boolean isRequired(Key key, Config config) {
        if (key.required()) {
            return true;
        }
        Optional<String> condition = key.constraints().requiredIf();
        if (condition.isEmpty() || !condition.get().contains("=")) {
            return false;
        }
        String[] parts = condition.get().split("=", 2);
        ConfigValue other = config.getConfigValue(parts[0].trim());
        return other != null && other.getValue() != null && other.getValue().trim().equals(parts[1].trim());
    }

    /**
     * Names of the required keys the given configuration does not provide (a required key whose value is
     * blank counts as missing), and, when a security module expects a roles mapping, {@code role:<name>} for
     * every role no identity-provider group is mapped to.
     */
    public List<String> missing(Config config) {
        List<String> out = new ArrayList<>();
        for (Key key : keys) {
            if (key.pattern() || !isRequired(key, config)) {
                continue;
            }
            ConfigValue value = config.getConfigValue(key.name());
            if (value == null || value.getValue() == null || value.getValue().isBlank()) {
                out.add(key.name());
            }
        }
        if (rolesMappingExpected()) {
            roleMappings(config).forEach((role, groups) -> {
                if (groups.isEmpty()) {
                    out.add("role:" + role);
                }
            });
        }
        return out;
    }

    /** Present values that do not satisfy their key's type or constraints, as {@code key: problem}. */
    public List<String> violations(Config config) {
        List<String> out = new ArrayList<>();
        for (Key key : keys) {
            if (key.pattern()) {
                continue;
            }
            ConfigValue value = config.getConfigValue(key.name());
            if (value == null || value.getValue() == null) {
                continue;
            }
            check(key, value.getValue()).ifPresent(problem -> out.add(key.name() + ": " + problem));
        }
        return out;
    }

    /** Checks one value against a key's type and constraints; the same rules as the Ansible validator. */
    public static Optional<String> check(Key key, String value) {
        if (key.secret()) {
            return value.isBlank() ? Optional.of("secret is empty") : Optional.empty();
        }
        String text = value.trim();
        String type = key.type();
        Constraints c = key.constraints();
        try {
            switch (type) {
                case "int", "Integer", "long", "Long", "short", "Short" -> {
                    long number = Long.parseLong(text);
                    if (c.min().isPresent() && number < Long.parseLong(c.min().get())) {
                        return Optional.of(text + " is below the minimum " + c.min().get());
                    }
                    if (c.max().isPresent() && number > Long.parseLong(c.max().get())) {
                        return Optional.of(text + " is above the maximum " + c.max().get());
                    }
                }
                case "BigDecimal", "double", "Double", "float", "Float" -> {
                    BigDecimal number = new BigDecimal(text);
                    if (c.min().isPresent() && number.compareTo(new BigDecimal(c.min().get())) < 0) {
                        return Optional.of(text + " is below the minimum " + c.min().get());
                    }
                    if (c.max().isPresent() && number.compareTo(new BigDecimal(c.max().get())) > 0) {
                        return Optional.of(text + " is above the maximum " + c.max().get());
                    }
                }
                case "boolean", "Boolean" -> {
                    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                        return Optional.of("'" + text + "' is not a boolean");
                    }
                }
                case "Duration" -> parseDuration(text);
                default -> {
                    // strings, lists, paths, enums: constraints only
                }
            }
        } catch (RuntimeException e) {
            return Optional.of("'" + text + "' is not a valid " + type);
        }
        if (!c.values().isEmpty() && c.values().stream().noneMatch(v -> v.equalsIgnoreCase(text)
                || v.replace('_', '-').equalsIgnoreCase(text))) {
            return Optional.of("'" + text + "' is not one of " + c.values());
        }
        if (c.pattern().isPresent() && !Pattern.compile(c.pattern().get()).matcher(text).matches()) {
            return Optional.of("'" + text + "' does not match " + c.pattern().get());
        }
        return Optional.empty();
    }

    /** The Quarkus duration syntax: plain seconds, ISO-8601, or ISO-8601 without the PT prefix (5S, 1H30M). */
    static Duration parseDuration(String text) {
        String s = text.trim().toUpperCase(Locale.ROOT);
        if (s.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(s));
        }
        return Duration.parse(s.startsWith("P") ? s : "PT" + s);
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
                    .append(", \"phase\": ").append(Json.str(k.phase().json()));
            Constraints c = k.constraints();
            c.min().ifPresent(v -> sb.append(", \"min\": ").append(Json.str(v)));
            c.max().ifPresent(v -> sb.append(", \"max\": ").append(Json.str(v)));
            c.pattern().ifPresent(v -> sb.append(", \"pattern\": ").append(Json.str(v)));
            if (!c.values().isEmpty()) {
                sb.append(", \"values\": [");
                for (int j = 0; j < c.values().size(); j++) {
                    sb.append(Json.str(c.values().get(j))).append(j < c.values().size() - 1 ? ", " : "");
                }
                sb.append(']');
            }
            c.requiredIf().ifPresent(v -> sb.append(", \"required-if\": ").append(Json.str(v)));
            sb.append(", \"doc\": ").append(Json.str(k.doc()))
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
        sb.append("| Key | Type | Required | Default | Owner | Phase | Description |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (Key k : keys) {
            sb.append("| `").append(k.name()).append("` | ").append(k.type()).append(" | ")
                    .append(k.required() ? "yes" : "no").append(" | ")
                    .append(k.defaultValue().map(d -> "`" + d + "`").orElse("")).append(" | ")
                    .append(k.owner().name().toLowerCase(Locale.ROOT)).append(k.secret() ? " (secret)" : "")
                    .append(" | ").append(k.phase().json())
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

        public Builder platform(String name, String type, boolean required, String doc) {
            keys.add(new Key(name, type, required, Optional.empty(), false, Owner.PLATFORM, doc));
            return this;
        }

        public Builder platform(String name, String type, String defaultValue, String doc) {
            keys.add(new Key(name, type, false, Optional.ofNullable(defaultValue), false, Owner.PLATFORM, doc));
            return this;
        }

        public Builder platformSecret(String name, String doc) {
            keys.add(new Key(name, "String", true, Optional.empty(), true, Owner.PLATFORM, doc));
            return this;
        }

        public Builder role(String role) {
            if (role != null && !role.isBlank() && !roles.contains(role.trim())) {
                roles.add(role.trim());
            }
            return this;
        }

        /** Adds a key unless one with the same name is already there (the application's, or an earlier module's). */
        public Builder addIfAbsent(Key key) {
            if (keys.stream().noneMatch(k -> k.name().equals(key.name()))) {
                keys.add(key);
            }
            return this;
        }

        /** Adds a key, replacing any earlier one with the same name (a variation module overriding a default). */
        public Builder replace(Key key) {
            keys.removeIf(k -> k.name().equals(key.name()));
            keys.add(key);
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
            Collections.sort(sortedRoles);
            return new ConfigContract(keys, sortedRoles);
        }
    }
}
