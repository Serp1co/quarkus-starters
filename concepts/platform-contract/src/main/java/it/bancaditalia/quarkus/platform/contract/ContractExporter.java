package it.bancaditalia.quarkus.platform.contract;

import io.smallrye.config.ConfigMapping;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Derives an application's config contract from its compiled classes and the platform descriptors on its
 * classpath, and writes it as {@code META-INF/config-contract.json}. The parent POM runs it on every
 * application module after compilation (exec-maven-plugin, phase process-classes), so the developer never
 * writes or maintains the file and the tests, the packaged artifact and the deploy role all see the same one.
 * <p>
 * Application half: every {@code @ConfigMapping} interface (keys, defaults, docs, secrets); every messaging
 * channel ({@code @Incoming}, {@code @Outgoing}, {@code @Channel}) combined with the channel key templates
 * of the messaging modules; every role named in {@code @RolesAllowed}, so that the platform knows which
 * identity-provider groups it must map. Platform half: the keys each bdi-config-* module declares.
 */
public final class ContractExporter {

    private static final String INCOMING = "org.eclipse.microprofile.reactive.messaging.Incoming";
    private static final String OUTGOING = "org.eclipse.microprofile.reactive.messaging.Outgoing";
    private static final String CHANNEL = "org.eclipse.microprofile.reactive.messaging.Channel";
    private static final String ROLES_ALLOWED = "jakarta.annotation.security.RolesAllowed";

    private ContractExporter() {
    }

    /** args: classes directory, output file, application name. */
    public static void main(String[] args) throws IOException {
        Path classes = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String application = args.length > 2 ? args[2] : "application";
        ConfigContract contract = export(classes, Thread.currentThread().getContextClassLoader(), application);
        Files.createDirectories(out.getParent());
        Files.writeString(out, contract.toJson(), StandardCharsets.UTF_8);
        System.out.println("[bdi-contract] " + application + ": " + contract.keys().size() + " keys -> " + out);
    }

    public static ConfigContract export(Path classes, ClassLoader loader, String application) throws IOException {
        List<Class<?>> mappings = new ArrayList<>();
        Set<String> incoming = new TreeSet<>();
        Set<String> outgoing = new TreeSet<>();
        Set<String> roles = new TreeSet<>();
        for (String name : classNames(classes)) {
            Class<?> type;
            try {
                type = Class.forName(name, false, loader);
                if (type.isInterface() && type.isAnnotationPresent(ConfigMapping.class)) {
                    mappings.add(type);
                }
                channels(type, incoming, outgoing);
                roles(type, roles);
            } catch (Throwable ignored) {
                // a class that cannot be loaded here (optional dependency, generated code) has no contract to give
            }
        }
        mappings.sort(java.util.Comparator.comparing(Class::getName));

        ConfigContract.Builder builder = ConfigContract.builder();
        for (Class<?> mapping : mappings) {
            builder.mapping(mapping);
        }
        roles.forEach(builder::role);
        for (PlatformDescriptor descriptor : PlatformDescriptor.load(loader)) {
            descriptor.keys().forEach(builder::addIfAbsent);
            for (String channel : incoming) {
                descriptor.incoming().forEach(k -> builder.addIfAbsent(k.forChannel("incoming", channel, application)));
            }
            for (String channel : outgoing) {
                descriptor.outgoing().forEach(k -> builder.addIfAbsent(k.forChannel("outgoing", channel, application)));
            }
        }
        return builder.build();
    }

    static List<String> classNames(Path classes) throws IOException {
        if (!Files.isDirectory(classes)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(classes)) {
            return files.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classes.relativize(p).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                    .filter(n -> !n.equals("module-info") && !n.endsWith("package-info"))
                    .sorted()
                    .toList();
        }
    }

    private static void channels(Class<?> type, Set<String> incoming, Set<String> outgoing) {
        for (Method method : type.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getName();
                if (INCOMING.equals(name)) {
                    incoming.add(value(annotation));
                } else if (OUTGOING.equals(name)) {
                    outgoing.add(value(annotation));
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            for (Annotation annotation : field.getAnnotations()) {
                if (CHANNEL.equals(annotation.annotationType().getName())) {
                    if (field.getType().getSimpleName().contains("Emitter")) {
                        outgoing.add(value(annotation));
                    } else {
                        incoming.add(value(annotation));
                    }
                }
            }
        }
    }

    /** Every role named by {@code @RolesAllowed} on the class or its methods. */
    private static void roles(Class<?> type, Set<String> roles) {
        for (Annotation annotation : type.getAnnotations()) {
            if (ROLES_ALLOWED.equals(annotation.annotationType().getName())) {
                roles.addAll(values(annotation));
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                if (ROLES_ALLOWED.equals(annotation.annotationType().getName())) {
                    roles.addAll(values(annotation));
                }
            }
        }
    }

    private static List<String> values(Annotation annotation) {
        try {
            Object value = annotation.annotationType().getMethod("value").invoke(annotation);
            return value instanceof String[] array ? List.of(array) : List.of(String.valueOf(value));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String value(Annotation annotation) {
        try {
            return String.valueOf(annotation.annotationType().getMethod("value").invoke(annotation));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
