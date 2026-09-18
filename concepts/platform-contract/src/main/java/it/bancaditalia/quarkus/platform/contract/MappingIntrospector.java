package it.bancaditalia.quarkus.platform.contract;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns a {@link ConfigMapping} interface into the list of keys it declares, following the SmallRye
 * Config mapping rules: kebab-case names, {@link WithName}, {@link WithParentName}, {@link WithDefault},
 * nested groups, {@link Optional}, collections (indexed or comma separated) and maps (wildcard keys).
 */
final class MappingIntrospector {

    private MappingIntrospector() {
    }

    static List<ConfigContract.Key> introspect(Class<?> mappingInterface) {
        ConfigMapping mapping = mappingInterface.getAnnotation(ConfigMapping.class);
        if (mapping == null) {
            throw new IllegalArgumentException(mappingInterface.getName() + " is not annotated with @ConfigMapping");
        }
        List<ConfigContract.Key> keys = new ArrayList<>();
        walk(mappingInterface, mapping.prefix(), mapping.namingStrategy(), keys);
        return keys;
    }

    private static void walk(Class<?> group, String prefix, ConfigMapping.NamingStrategy naming,
            List<ConfigContract.Key> out) {
        Method[] methods = group.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)); // reflection order is unspecified
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.isDefault() || method.isSynthetic()
                    || method.getParameterCount() != 0) {
                continue;
            }
            String key = method.isAnnotationPresent(WithParentName.class)
                    ? prefix
                    : join(prefix, propertyName(method, naming));
            describe(method.getGenericReturnType(), method, key, naming, out, false);
        }
    }

    private static void describe(Type type, Method method, String key, ConfigMapping.NamingStrategy naming,
            List<ConfigContract.Key> out, boolean optional) {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (raw == Optional.class) {
                describe(args[0], method, key, naming, out, true);
                return;
            }
            if (Collection.class.isAssignableFrom(raw)) {
                if (isGroup(args[0])) {
                    walk((Class<?>) args[0], key + "[*]", naming, out);
                    return;
                }
                out.add(leaf(key, "list<" + simpleName(args[0]) + ">", method, optional, false));
                return;
            }
            if (Map.class.isAssignableFrom(raw)) {
                String pattern = key + ".*";
                if (isGroup(args[1])) {
                    walk((Class<?>) args[1], pattern, naming, out);
                    return;
                }
                out.add(leaf(pattern, "map<" + simpleName(args[1]) + ">", method, optional, true));
                return;
            }
            out.add(leaf(key, simpleName(type), method, optional, false));
            return;
        }
        if (type instanceof Class<?> clazz && isGroup(clazz)) {
            walk(clazz, key, naming, out);
            return;
        }
        out.add(leaf(key, simpleName(type), method, optional, false));
    }

    private static ConfigContract.Key leaf(String key, String type, Method method, boolean optional, boolean map) {
        WithDefault withDefault = method.getAnnotation(WithDefault.class);
        Optional<String> defaultValue = withDefault == null ? Optional.empty() : Optional.of(withDefault.value());
        boolean required = !optional && !map && defaultValue.isEmpty();
        Doc doc = method.getAnnotation(Doc.class);
        return new ConfigContract.Key(key, type, required, defaultValue, method.isAnnotationPresent(Secret.class),
                ConfigContract.Owner.APPLICATION, doc == null ? "" : doc.value(), ConfigContract.Phase.RUNTIME,
                constraints(method));
    }

    /**
     * Bean Validation constraints on the method ({@code @Min}, {@code @Max}, {@code @DecimalMin}, {@code @DecimalMax},
     * {@code @Pattern}) and the constants of an enum return type, read by annotation name so that the contract
     * library does not depend on Bean Validation.
     */
    private static ConfigContract.Constraints constraints(Method method) {
        Optional<String> min = Optional.empty(), max = Optional.empty(), pattern = Optional.empty();
        for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getName();
            switch (name) {
                case "jakarta.validation.constraints.Min", "jakarta.validation.constraints.DecimalMin" -> min = Optional.of(member(annotation, "value"));
                case "jakarta.validation.constraints.Max", "jakarta.validation.constraints.DecimalMax" -> max = Optional.of(member(annotation, "value"));
                case "jakarta.validation.constraints.Pattern" -> pattern = Optional.of(member(annotation, "regexp"));
                default -> {
                }
            }
        }
        List<String> values = new ArrayList<>();
        Type returned = method.getGenericReturnType();
        if (returned instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class) {
            returned = parameterized.getActualTypeArguments()[0];
        }
        if (returned instanceof Class<?> clazz && clazz.isEnum()) {
            for (Object constant : clazz.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
        }
        return new ConfigContract.Constraints(min, max, pattern, values, Optional.empty());
    }

    private static String member(java.lang.annotation.Annotation annotation, String member) {
        try {
            return String.valueOf(annotation.annotationType().getMethod(member).invoke(annotation));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A nested mapping group is any non-JDK interface; JDK interfaces (Path, CharSequence...) are converted leaves. */
    private static boolean isGroup(Type type) {
        return type instanceof Class<?> clazz && clazz.isInterface() && !clazz.getName().startsWith("java.");
    }

    private static String simpleName(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.getSimpleName();
        }
        if (type instanceof ParameterizedType parameterized) {
            return simpleName(parameterized.getRawType()) + Arrays.stream(parameterized.getActualTypeArguments())
                    .map(MappingIntrospector::simpleName)
                    .collect(Collectors.joining(",", "<", ">"));
        }
        return type.getTypeName();
    }

    static String propertyName(Method method, ConfigMapping.NamingStrategy naming) {
        WithName withName = method.getAnnotation(WithName.class);
        if (withName != null) {
            return withName.value();
        }
        return switch (naming) {
            case VERBATIM -> method.getName();
            case SNAKE_CASE -> humps(method.getName(), '_');
            default -> humps(method.getName(), '-');
        };
    }

    /** camelCase to kebab-case (or snake_case): maxURLSize becomes max-url-size, the SmallRye way. */
    static String humps(String name, char separator) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean boundary = i > 0 && (Character.isLowerCase(name.charAt(i - 1))
                        || Character.isDigit(name.charAt(i - 1))
                        || (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1))));
                if (boundary) {
                    sb.append(separator);
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String join(String prefix, String name) {
        return prefix == null || prefix.isEmpty() ? name : prefix + "." + name;
    }
}
