package it.bancaditalia.quarkus.config.core;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Discovers every {@code META-INF/bdi-defaults.yaml} on the classpath (one per bdi-config-* module) and
 * exposes each as a YAML config source named {@code BdiDefaults[module]} with ordinal {@link #ORDINAL_DEFAULT}
 * (or the {@code x-bdi-ordinal} the file declares).
 * <p>
 * The ordinal is the point: 100 sits above Quarkus' own defaults and below {@code application.yaml} (250),
 * the files the platform renders through {@code QUARKUS_CONFIG_LOCATIONS} (260), environment variables
 * (300) and system properties (400). A standardized default is therefore always overridable by the
 * developer for the inner loop and by the platform for an environment, and the conformance endpoint shows
 * which module a value came from. Profile sections ({@code "%dev":}, {@code "%test":}) work as in any YAML.
 * Registered through {@code META-INF/services/io.smallrye.config.ConfigSourceFactory}, which Quarkus
 * honours at build time and at run time alike.
 */
public class BdiDefaultsConfigSourceFactory implements ConfigSourceFactory {

    public static final String RESOURCE = "META-INF/bdi-defaults.yaml";
    public static final int ORDINAL_DEFAULT = 100;

    /** A module whose defaults must win over another module's declares {@code x-bdi-ordinal: 110} at the top level. */
    private static final Pattern ORDINAL = Pattern.compile("(?m)^x-bdi-ordinal:\\s*(\\d+)\\s*$");
    private static final Pattern JAR_NAME = Pattern.compile("([A-Za-z][A-Za-z0-9-]*?)-[0-9][^/]*\\.jar!");
    private static final Pattern MODULE_DIR = Pattern.compile("/([^/]+)/target/classes/");

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = BdiDefaultsConfigSourceFactory.class.getClassLoader();
        }
        List<ConfigSource> sources = new ArrayList<>();
        try {
            Enumeration<URL> resources = loader.getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream()) {
                    String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    sources.add(new YamlConfigSource("BdiDefaults[" + moduleName(url) + "]", yaml, ordinal(yaml)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
        sources.sort(Comparator.comparing(ConfigSource::getName));
        return sources;
    }

    static int ordinal(String yaml) {
        Matcher m = ORDINAL.matcher(yaml);
        return m.find() ? Integer.parseInt(m.group(1)) : ORDINAL_DEFAULT;
    }

    /** {@code .../bdi-config-jpa-1.0.0.jar!/META-INF/...} or {@code .../bdi-config-jpa/target/classes/...} to {@code bdi-config-jpa}. */
    static String moduleName(URL url) {
        String location = url.toString();
        Matcher jar = JAR_NAME.matcher(location);
        if (jar.find()) {
            return jar.group(1);
        }
        Matcher dir = MODULE_DIR.matcher(location);
        if (dir.find()) {
            return dir.group(1);
        }
        return location;
    }
}
