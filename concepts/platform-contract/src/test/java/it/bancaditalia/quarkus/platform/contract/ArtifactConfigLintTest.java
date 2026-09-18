package it.bancaditalia.quarkus.platform.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactConfigLintTest {

    private static final ConfigContract CONTRACT = ConfigContract.builder()
            .platform("quarkus.datasource.jdbc.url", "String", true, "db")
            .platformSecret("quarkus.datasource.password", "db password")
            .platform("quarkus.http.port", "int", "8080", "port")
            .addIfAbsent(new ConfigContract.Key("quarkus.quartz.clustered", "boolean", false, java.util.Optional.of("true"), false,
                    ConfigContract.Owner.PLATFORM, "", ConfigContract.Phase.BUILD_TIME, ConfigContract.Constraints.NONE))
            .addIfAbsent(new ConfigContract.Key("quarkus.datasource.*.password", "String", false, java.util.Optional.empty(), true,
                    ConfigContract.Owner.PLATFORM, "named datasource password"))
            .addIfAbsent(new ConfigContract.Key("ledger.currency", "String", false, java.util.Optional.of("EUR"), false,
                    ConfigContract.Owner.APPLICATION, ""))
            .build();

    @Test
    void refusesEnvironmentOwnedValuesAndSecretsOutsideTheInnerLoop(@TempDir Path classes) throws IOException {
        Files.writeString(classes.resolve("application.yaml"), """
                quarkus:
                  quartz:
                    clustered: true            # build-time: allowed
                  hibernate-orm:
                    cache:
                      "it.example.Type":
                        expiration:
                          max-idle: 10M        # unknown Quarkus key: accepted, noted
                  http:
                    port: 8081                 # platform runtime key: refused
                  datasource:
                    jdbc:
                      url: jdbc:postgresql://prod-db/ledger   # platform runtime key: refused
                    password: hunter2          # secret: refused
                    reporting:
                      password: hunter3        # secret by wildcard: refused
                ledger:
                  currency: EUR                # application default: allowed
                  api-token: abc               # looks like a secret: refused
                "%dev":
                  quarkus:
                    datasource:
                      password: dev            # inner loop: allowed
                "%prod":
                  quarkus:
                    log:
                      level: DEBUG             # environment profile: refused
                """);
        Files.writeString(classes.resolve("application-uat.properties"), "quarkus.http.port=9090\n");
        ArtifactConfigLint.Report report = ArtifactConfigLint.lint(classes, CONTRACT);
        List<String> violations = report.violations();
        assertTrue(violations.stream().anyMatch(v -> v.contains("quarkus.http.port") && v.contains("runtime")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("quarkus.datasource.jdbc.url")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("quarkus.datasource.password") && v.contains("contract")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("quarkus.datasource.reporting.password")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("ledger.api-token") && v.contains("looks like")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("%prod.quarkus.log.level")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("application-uat.properties")), violations.toString());
        assertEquals(7, violations.size(), violations.toString());
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("max-idle")), report.notes().toString());
        assertTrue(violations.stream().noneMatch(v -> v.contains("clustered") || v.contains("currency") || v.contains("%dev")), violations.toString());
    }

    @Test
    void stillUnderstandsPropertiesFilesAndProfileLists(@TempDir Path classes) throws IOException {
        Files.writeString(classes.resolve("application.properties"), "%dev,test.a=1\n%uat.b=2\nquarkus.http.port=8080\n");
        List<String> violations = ArtifactConfigLint.lint(classes, CONTRACT).violations();
        assertEquals(2, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("uat") || violations.get(1).contains("uat"), violations.toString());
    }

    @Test
    void acceptsAnInnerLoopOnlyArtifact(@TempDir Path classes) throws IOException {
        Files.writeString(classes.resolve("application.yaml"), "\"%dev\":\n  quarkus:\n    http:\n      port: 8081\n\"%test\":\n  ledger:\n    currency: USD\n");
        ArtifactConfigLint.Report report = ArtifactConfigLint.lint(classes, CONTRACT);
        assertEquals(List.of(), report.violations());
        assertEquals(2, report.checked());
    }
}
