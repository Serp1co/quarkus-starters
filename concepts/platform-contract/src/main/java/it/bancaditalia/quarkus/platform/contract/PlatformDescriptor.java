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
 * application declares ({@code {channel}} and {@code {application}} are substituted by the exporter).
 * <pre>
 * { "module": "bdi-config-jpa",
 *   "keys": [ { "name": "quarkus.datasource.jdbc.url", "type": "String", "required": true, "doc": "..." } ],
 *   "channels": { "incoming": [ { "suffix": "address", "type": "String", "default": "{channel}", "doc": "..." } ],
 *                 "outgoing": [ ... ] } }
 * </pre>
 */
public record PlatformDescriptor(String module, List<ConfigContract.Key> keys, List<ChannelKey> incoming,
        List<ChannelKey> outgoing) {

    public static final String RESOURCE = "META-INF/bdi-contract-platform.json";

    public record ChannelKey(String suffix, String type, boolean required, Optional<String> defaultValue, boolean secret,
            String doc) {

        ConfigContract.Key forChannel(String direction, String channel, String application) {
            String name = "mp.messaging." + direction + "." + channel + "." + suffix;
            Optional<String> value = defaultValue.map(d -> d.replace("{channel}", channel).replace("{application}", application));
            return new ConfigContract.Key(name, type, required, value, secret, ConfigContract.Owner.PLATFORM, doc);
        }
    }

    public static PlatformDescriptor parse(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            List<ConfigContract.Key> keys = new ArrayList<>();
            for (JsonNode k : root.path("keys")) {
                keys.add(new ConfigContract.Key(k.path("name").asText(), k.path("type").asText("String"),
                        k.path("required").asBoolean(false),
                        k.hasNonNull("default") ? Optional.of(k.path("default").asText()) : Optional.empty(),
                        k.path("secret").asBoolean(false), ConfigContract.Owner.PLATFORM, k.path("doc").asText("")));
            }
            return new PlatformDescriptor(root.path("module").asText("?"), keys,
                    channelKeys(root.path("channels").path("incoming")), channelKeys(root.path("channels").path("outgoing")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<ChannelKey> channelKeys(JsonNode array) {
        List<ChannelKey> out = new ArrayList<>();
        for (JsonNode k : array) {
            out.add(new ChannelKey(k.path("suffix").asText(), k.path("type").asText("String"), k.path("required").asBoolean(false),
                    k.hasNonNull("default") ? Optional.of(k.path("default").asText()) : Optional.empty(),
                    k.path("secret").asBoolean(false), k.path("doc").asText("")));
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
