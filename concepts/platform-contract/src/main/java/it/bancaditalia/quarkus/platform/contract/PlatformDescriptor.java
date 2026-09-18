package it.bancaditalia.quarkus.platform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * What a bdi-config-* module declares for the platform, in its {@code META-INF/bdi-contract-platform.json}:
 * the Quarkus keys of the extensions it configures, and the key templates of the messaging channels the
 * application declares ({@code {channel}} and {@code {application}} are substituted by the exporter), and likewise
 * {@code rest-clients} / {@code grpc-clients} templates for every REST client and gRPC client the application injects
 * ({@code {client}} substituted).
 * <pre>
 * { "module": "bdi-config-jpa",
 *   "keys": [ { "name": "quarkus.datasource.jdbc.url", "type": "String", "required": true, "doc": "..." } ],
 *   "channels": { "incoming": [ { "suffix": "address", "type": "String", "default": "{channel}", "doc": "..." } ],
 *                 "outgoing": [ ... ] } }
 * </pre>
 */
public record PlatformDescriptor(String module, List<ConfigContract.Key> keys, List<ChannelKey> incoming,
        List<ChannelKey> outgoing, List<String> replaces, List<ChannelKey> restClients, List<ChannelKey> grpcClients) {

    public static final String REST_CLIENT_PREFIX = "quarkus.rest-client.";
    public static final String GRPC_CLIENT_PREFIX = "quarkus.grpc.clients.";

    /** A key may carry {@code "phase": "build-time"}, {@code "min"/"max"/"pattern"/"values"/"required-if"}, and
     *  {@code "replaces": true} when a variation module overrides the key of the module it builds on. */
    public boolean replaces(ConfigContract.Key key) {
        return replaces.contains(key.name());
    }

    public static final String RESOURCE = "META-INF/bdi-contract-platform.json";

    public record ChannelKey(String suffix, String type, boolean required, Optional<String> defaultValue, boolean secret,
            String doc, ConfigContract.Constraints constraints) {

        public ChannelKey(String suffix, String type, boolean required, Optional<String> defaultValue, boolean secret, String doc) {
            this(suffix, type, required, defaultValue, secret, doc, ConfigContract.Constraints.NONE);
        }

        ConfigContract.Key forChannel(String direction, String channel, String application) {
            return forName("mp.messaging." + direction + ".", channel, application);
        }

        /** A key template instantiated for a named resource: {@code <prefix><name>.<suffix>}, the name quoted if it has dots. */
        ConfigContract.Key forName(String prefix, String name, String application) {
            String key = prefix + (name.contains(".") ? "\"" + name + "\"" : name) + "." + suffix;
            Optional<String> value = defaultValue.map(d -> d.replace("{channel}", name).replace("{client}", name).replace("{application}", application));
            return new ConfigContract.Key(key, type, required, value, secret, ConfigContract.Owner.PLATFORM,
                    doc.replace("{channel}", name).replace("{client}", name), ConfigContract.Phase.RUNTIME, constraints);
        }
    }

    public static PlatformDescriptor parse(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            List<ConfigContract.Key> keys = new ArrayList<>();
            List<String> replaces = new ArrayList<>();
            for (JsonNode k : root.path("keys")) {
                ConfigContract.Key key = ConfigContract.keyFromJson(k, ConfigContract.Owner.PLATFORM);
                keys.add(key);
                if (k.path("replaces").asBoolean(false)) {
                    replaces.add(key.name());
                }
            }
            return new PlatformDescriptor(root.path("module").asText("?"), keys,
                    channelKeys(root.path("channels").path("incoming")), channelKeys(root.path("channels").path("outgoing")),
                    replaces, channelKeys(root.path("rest-clients")), channelKeys(root.path("grpc-clients")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<ChannelKey> channelKeys(JsonNode array) {
        List<ChannelKey> out = new ArrayList<>();
        for (JsonNode k : array) {
            ConfigContract.Key parsed = ConfigContract.keyFromJson(k, ConfigContract.Owner.PLATFORM); // constraints, default, secret
            out.add(new ChannelKey(k.path("suffix").asText(), parsed.type(), parsed.required(), parsed.defaultValue(),
                    parsed.secret(), parsed.doc(), parsed.constraints()));
        }
        return out;
    }

    /** Every descriptor on the classpath, ordered by module name for a deterministic contract. */
    public static List<PlatformDescriptor> load(ClassLoader loader) {
        List<PlatformDescriptor> out = new ArrayList<>();
        try {
            Enumeration<URL> urls = loader.getResources(RESOURCE);
            while (urls.hasMoreElements()) {
                try (InputStream in = urls.nextElement().openStream()) {
                    out.add(parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(Comparator.comparing(PlatformDescriptor::module));
        return out;
    }
}
