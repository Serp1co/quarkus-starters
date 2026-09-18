package it.bancaditalia.quarkus.platform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The build-time enforcement of design note §2.1: the artifact is built once and configured per environment
 * by the platform, so the configuration it packages may carry build settings and inner-loop values only.
 * Run by the platform parent POM on every application ({@link #main}), against every configuration resource
 * in the compiled classes and the contract the exporter just derived. Not a test the application may drop.
 * <p>
 * Refused:
 * <ul>
 * <li>a profile other than {@code dev} and {@code test}, as a section, a key prefix or a profile-specific file;</li>
 * <li>outside {@code %dev}/{@code %test}: a value for a secret key of the contract, or for any key whose name says
 * secret ({@code password}, {@code secret}, {@code token}, {@code credential}, ...);</li>
 * <li>outside {@code %dev}/{@code %test}: a value for a platform-owned runtime key of the contract (the URL of the
 * database, a port, a broker): those belong to the inventory of each environment.</li>
 * </ul>
 * Allowed: build-time keys (a fixed choice of the artifact), application-owned defaults, unknown keys (reported).
 */
public final class ArtifactConfigLint {

    public static final Set<String> INNER_LOOP_PROFILES = Set.of("dev", "test");
    static final Pattern SECRET_NAME = Pattern.compile("(?i).*(password|passwd|secret|token|credential|passphrase|private-key|api-key)$");
    private static final List<String> CONFIG_FILES = List.of("application.yaml", "application.yml", "application.properties",
            "META-INF/microprofile-config.properties");

    private ArtifactConfigLint() {
    }

    /** args: classes directory, contract file (as written by the exporter). Exit 1 with the violations listed. */
    public static void main(String[] args) throws IOException {
        Path classes = Path.of(args[0]);
        ConfigContract contract = ConfigContract.fromJson(Files.readString(Path.of(args[1]), StandardCharsets.UTF_8));
        Report report = lint(classes, contract);
        report.notes().forEach(n -> System.out.println("[bdi-lint] " + n));
        if (!report.violations().isEmpty()) {
            System.err.println("[bdi-lint] the artifact carries environment-owned configuration (design note 2.1):");
            report.violations().forEach(v -> System.err.println("[bdi-lint]   " + v));
            System.err.println("[bdi-lint] move these values to the platform inventory, or under \"%dev\" / \"%test\" if they are inner-loop values");
            System.exit(1);
        }
        System.out.println("[bdi-lint] packaged configuration is environment-free (" + report.checked() + " keys checked)");
    }

    public record Report(List<String> violations, List<String> notes, int checked) {
    }

    /** One packaged configuration entry: file, profile ("" for none), key, value. */
    record Entry(String file, String profile, String key, String value) {
    }

    public static Report lint(Path classes, ConfigContract contract) throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        if (Files.isDirectory(classes)) {
            for (String name : CONFIG_FILES) {
                Path file = classes.resolve(name);
                if (Files.isRegularFile(file)) {
                    entries.addAll(read(file, name));
                }
            }
            try (Stream<Path> files = Files.list(classes)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (name.matches("application-[^.]+\\.(yaml|yml|properties)")) {
                        String profile = name.substring("application-".length(), name.lastIndexOf('.'));
                        if (!INNER_LOOP_PROFILES.contains(profile)) {
                            violations.add(name + ": profile-specific file for '" + profile + "'; the platform renders application-<env>.yaml");
                        }
                    }
                }
            }
        }
        for (Entry e : entries) {
            String where = e.file() + ": " + (e.profile().isEmpty() ? "" : "%" + e.profile() + ".") + e.key();
            if (!e.profile().isEmpty()) {
                for (String profile : e.profile().split(",")) {
                    if (!INNER_LOOP_PROFILES.contains(profile.trim())) {
                        violations.add(where + ": profile '" + profile.trim() + "' is an environment; only dev and test may appear in the artifact");
                        break;
                    }
                }
                continue; // inner-loop values may be anything
            }
            var match = contract.match(e.key());
            if (match.isPresent()) {
                ConfigContract.Key key = match.get();
                if (key.secret()) {
                    violations.add(where + ": a secret (contract); secrets are delivered from the vault, never packaged");
                } else if (key.owner() == ConfigContract.Owner.PLATFORM && !key.buildTime()) {
                    violations.add(where + ": platform-owned runtime key; the environment renders it (inventory), the artifact must not fix it");
                }
            } else if (SECRET_NAME.matcher(lastSegment(e.key())).matches()) {
                violations.add(where + ": looks like a secret; secrets are delivered from the vault, never packaged");
            } else if (e.key().startsWith("quarkus.") && !e.key().startsWith("quarkus.config.")) {
                notes.add(where + ": Quarkus key outside the contract, accepted as a build setting");
            }
        }
        return new Report(violations, notes, entries.size());
    }

    private static String lastSegment(String key) {
        String k = key.replace("\"", "");
        return k.substring(k.lastIndexOf('.') + 1);
    }

    static List<Entry> read(Path file, String name) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<Entry> out = new ArrayList<>();
        if (name.endsWith(".properties")) {
            Properties properties = new Properties();
            properties.load(new StringReader(text));
            for (String property : properties.stringPropertyNames()) {
                String profile = "", key = property;
                if (property.startsWith("%")) {
                    int dot = property.indexOf('.');
                    profile = dot > 0 ? property.substring(1, dot) : property.substring(1);
                    key = dot > 0 ? property.substring(dot + 1) : "";
                }
                out.add(new Entry(name, profile, key, properties.getProperty(property)));
            }
            return out;
        }
        JsonNode root = new YAMLMapper().readTree(text);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return out;
        }
        Map<String, String> flat = new LinkedHashMap<>();
        flatten(root, "", flat);
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String key = entry.getKey(), profile = "";
            if (key.startsWith("%")) {
                int dot = key.indexOf('.');
                profile = dot > 0 ? key.substring(1, dot) : key.substring(1);
                key = dot > 0 ? key.substring(dot + 1) : "";
            }
            out.add(new Entry(name, profile, key, entry.getValue()));
        }
        return out;
    }

    private static void flatten(JsonNode node, String prefix, Map<String, String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String segment = field.getKey().contains(".") && !field.getKey().startsWith("%") ? "\"" + field.getKey() + "\"" : field.getKey();
                flatten(field.getValue(), prefix.isEmpty() ? segment : prefix + "." + segment, out);
            }
        } else if (node.isArray()) {
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isValueNode()) {
                    items.add(item.asText());
                } else {
                    flatten(item, prefix + "[*]", out);
                }
            }
            if (!items.isEmpty()) {
                out.put(prefix, String.join(",", items));
            }
        } else {
            out.put(prefix, node.isNull() ? "" : node.asText());
        }
    }
}
