package it.bancaditalia.quarkus.platform.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;

/**
 * The configuration contract of one application: every key the platform may or must render for it.
 * <p>
 * Built from the application's {@code @ConfigMapping} interfaces (what the developer declared) plus the
 * Quarkus keys of the extensions it uses (what the platform owns). Exported as JSON so that Ansible
 * Automation Platform can validate the rendered {@code application.properties} before a deploy, and
 * resolved at runtime by the conformance endpoint so that ops can see which config source served each key.
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

    /** One contract key resolved against the live configuration. */
    public record Echo(String key, boolean present, String value, String source, boolean required, boolean secret) {
    }

    public static final String MASK = "******";

    private final List<Key> keys;

    private ConfigContract(List<Key> keys) {
        this.keys = List.copyOf(keys);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Key> keys() {
        return keys;
    }

    public Optional<Key> key(String name) {
        return keys.stream().filter(k -> k.name().equals(name)).findFirst();
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
                    key.required(), key.secret()));
        }
        return out;
    }

    /** Names of the required keys the given configuration does not provide. */
    public List<String> missing(Config config) {
        return echo(config).stream().filter(e -> e.required() && !e.present()).map(Echo::key).toList();
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
        return sb.append("  ]\n}\n").toString();
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

        public ConfigContract build() {
            Set<String> seen = new HashSet<>();
            for (Key key : keys) {
                if (!seen.add(key.name())) {
                    throw new IllegalStateException("Duplicate contract key: " + key.name());
                }
            }
            return new ConfigContract(keys);
        }
    }
}
