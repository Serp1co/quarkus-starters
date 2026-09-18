package it.bancaditalia.quarkus.platform.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConfigContractTest {

    @ConfigMapping(prefix = "sample")
    interface SampleConfig {

        @WithDefault("EUR")
        @Doc("Currency")
        String currency();

        @Doc("Required amount")
        BigDecimal maxAmount();

        Optional<Duration> timeout();

        @WithName("api-key")
        @Secret
        String apiKey();

        Optional<List<String>> blockedIbans();

        Map<String, String> labels();

        Nested nested();

        interface Nested {
            @WithDefault("false")
            boolean enabled();

            Map<String, Endpoint> endpoints();
        }

        interface Endpoint {
            String url();

            @WithDefault("3")
            int retries();
        }
    }

    interface NotAMapping {
        String value();
    }

    @Test
    void introspectsMappingInterface() {
        ConfigContract contract = ConfigContract.builder().mapping(SampleConfig.class).build();
        Map<String, ConfigContract.Key> keys = contract.keys().stream()
                .collect(Collectors.toMap(ConfigContract.Key::name, Function.identity()));

        assertEquals(Optional.of("EUR"), keys.get("sample.currency").defaultValue());
        assertFalse(keys.get("sample.currency").required());
        assertEquals("Currency", keys.get("sample.currency").doc());
        assertEquals(ConfigContract.Owner.APPLICATION, keys.get("sample.currency").owner());

        assertTrue(keys.get("sample.max-amount").required());
        assertEquals("BigDecimal", keys.get("sample.max-amount").type());

        assertFalse(keys.get("sample.timeout").required());
        assertEquals("Duration", keys.get("sample.timeout").type());

        assertTrue(keys.get("sample.api-key").secret());
        assertTrue(keys.get("sample.api-key").required());

        assertEquals("list<String>", keys.get("sample.blocked-ibans").type());
        assertFalse(keys.get("sample.blocked-ibans").required());

        assertEquals("map<String>", keys.get("sample.labels.*").type());
        assertFalse(keys.get("sample.labels.*").required());
        assertTrue(keys.get("sample.labels.*").pattern());

        assertEquals(Optional.of("false"), keys.get("sample.nested.enabled").defaultValue());
        assertTrue(keys.get("sample.nested.endpoints.*.url").required());
        assertEquals(Optional.of("3"), keys.get("sample.nested.endpoints.*.retries").defaultValue());
        assertEquals(9, keys.size());
    }

    @Test
    void echoesValuesAndMasksSecrets() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(
                        Map.of("sample.max-amount", "10", "sample.api-key", "s3cr3t"), "inventory", 500))
                .build();
        ConfigContract contract = ConfigContract.builder()
                .mapping(SampleConfig.class)
                .platformSecret("db.password", "Database password")
                .build();

        List<ConfigContract.Echo> echo = contract.echo(config);
        ConfigContract.Echo amount = find(echo, "sample.max-amount");
        assertTrue(amount.present());
        assertEquals("10", amount.value());
        assertTrue(amount.source().contains("inventory"), amount.source());
        assertEquals(500, amount.ordinal());
        assertEquals(ConfigContract.MASK, find(echo, "sample.api-key").value());
        assertFalse(find(echo, "db.password").present());
        assertTrue(echo.stream().noneMatch(e -> e.key().contains("*")));
        assertEquals(List.of("db.password"), contract.missing(config));
    }

    @Test
    void unmappedRolesAreMissingWhenASecurityModuleExpectsAMapping() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(
                        Map.of("quarkus.http.auth.roles-mapping.\"APP-ADMINS\"", "admin,reader",
                                "quarkus.http.auth.roles-mapping.APP-READERS", "reader"), "platform", 300))
                .build();
        ConfigContract contract = ConfigContract.builder()
                .role("admin").role("operator").role("reader")
                .platform(ConfigContract.ROLES_MAPPING + "*", "String", false, "group -> roles")
                .build();
        Map<String, List<String>> mappings = contract.roleMappings(config);
        assertEquals(List.of("APP-ADMINS"), mappings.get("admin"));
        assertEquals(List.of("APP-ADMINS", "APP-READERS"), mappings.get("reader"));
        assertEquals(List.of(), mappings.get("operator"));
        assertEquals(List.of("role:operator"), contract.missing(config));
        assertEquals(List.of(), ConfigContract.builder().role("operator").build().missing(config), "no security module: nothing expected");
    }

    @Test
    void validatesTypesConstraintsAndConditionalRequirements() {
        ConfigContract contract = ConfigContract.builder()
                .platform("quarkus.management.port", "int", "9000", "port")
                .platformSecret("quarkus.datasource.password", "db")
                .addIfAbsent(new ConfigContract.Key("quarkus.oidc.application-type", "String", false, Optional.of("service"), false,
                        ConfigContract.Owner.PLATFORM, "", ConfigContract.Phase.RUNTIME,
                        new ConfigContract.Constraints(Optional.empty(), Optional.empty(), Optional.empty(), List.of("service", "web-app"), Optional.empty())))
                .addIfAbsent(new ConfigContract.Key("quarkus.oidc.credentials.secret", "String", false, Optional.empty(), true,
                        ConfigContract.Owner.PLATFORM, "", ConfigContract.Phase.RUNTIME,
                        new ConfigContract.Constraints(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.of("quarkus.oidc.application-type=web-app"))))
                .addIfAbsent(new ConfigContract.Key("quarkus.security.ldap.cache.max-age", "Duration", false, Optional.of("60S"), false,
                        ConfigContract.Owner.PLATFORM, ""))
                .addIfAbsent(new ConfigContract.Key("registry.max-page-size", "int", false, Optional.of("100"), false,
                        ConfigContract.Owner.APPLICATION, "", ConfigContract.Phase.RUNTIME,
                        new ConfigContract.Constraints(Optional.of("1"), Optional.of("1000"), Optional.empty(), List.of(), Optional.empty())))
                .build();
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "quarkus.management.port", "not-a-number",
                        "quarkus.datasource.password", "",
                        "quarkus.oidc.application-type", "web-app",
                        "quarkus.security.ldap.cache.max-age", "5S",
                        "registry.max-page-size", "5000"), "inventory", 500))
                .build();
        List<String> violations = contract.violations(config);
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("quarkus.management.port:") && v.contains("not a valid int")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("quarkus.datasource.password:") && v.contains("empty")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.startsWith("registry.max-page-size:") && v.contains("above the maximum")), violations.toString());
        assertEquals(3, violations.size(), violations.toString());
        // required-if: the client secret is required because the application type is web-app; a blank secret is missing
        assertEquals(List.of("quarkus.datasource.password", "quarkus.oidc.credentials.secret"), contract.missing(config));
        assertTrue(ConfigContract.check(contract.key("quarkus.oidc.application-type").orElseThrow(), "bearer").isPresent());
        assertTrue(ConfigContract.check(contract.key("quarkus.security.ldap.cache.max-age").orElseThrow(), "PT1H").isEmpty());
        assertTrue(ConfigContract.check(contract.key("quarkus.security.ldap.cache.max-age").orElseThrow(), "soon").isPresent());
        assertTrue(contract.match("quarkus.datasource.password").isPresent());
    }

    @Test
    void wildcardKeysMatchOneSegmentQuotedOrNot() {
        ConfigContract.Key key = new ConfigContract.Key("quarkus.datasource.*.password", "String", false, Optional.empty(), true,
                ConfigContract.Owner.PLATFORM, "");
        assertTrue(key.matches("quarkus.datasource.reporting.password"));
        assertTrue(key.matches("quarkus.datasource.\"reporting.eu\".password"));
        assertFalse(key.matches("quarkus.datasource.password"));
        assertFalse(key.matches("quarkus.datasource.a.b.password"));
    }

    @Test
    void rendersJsonAndMarkdown() {
        ConfigContract contract = ConfigContract.builder()
                .platform("quarkus.http.port", "int", "8080", "HTTP \"port\"")
                .platform("quarkus.datasource.jdbc.url", "String", true, "JDBC URL")
                .build();
        String json = contract.toJson();
        assertTrue(json.contains("{\"name\": \"quarkus.http.port\", \"type\": \"int\", \"required\": false, "
                + "\"default\": \"8080\", \"secret\": false, \"owner\": \"platform\", \"phase\": \"runtime\", \"doc\": \"HTTP \\\"port\\\"\"},"), json);
        assertTrue(json.contains("\"default\": null"), json);
        assertTrue(contract.toMarkdownTable()
                .contains("| `quarkus.datasource.jdbc.url` | String | yes |  | platform | runtime | JDBC URL |"));
    }

    @Test
    void rejectsDuplicatesAndNonMappings() {
        assertThrows(IllegalStateException.class, () -> ConfigContract.builder()
                .platform("a", "String", true, "")
                .platform("a", "String", true, "")
                .build());
        assertThrows(IllegalArgumentException.class, () -> ConfigContract.builder().mapping(NotAMapping.class));
    }

    @Test
    void convertsCamelHumps() {
        assertEquals("max-url-size", MappingIntrospector.humps("maxURLSize", '-'));
        assertEquals("max_amount", MappingIntrospector.humps("maxAmount", '_'));
        assertEquals("a1-b", MappingIntrospector.humps("a1B", '-'));
    }

    private static ConfigContract.Echo find(List<ConfigContract.Echo> echo, String key) {
        return echo.stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow();
    }
}
