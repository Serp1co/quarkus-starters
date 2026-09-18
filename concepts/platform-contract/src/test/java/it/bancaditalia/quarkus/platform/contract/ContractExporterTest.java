package it.bancaditalia.quarkus.platform.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContractExporterTest {

    @Test
    void derivesTheContractFromClassesAndDescriptors() throws Exception {
        // target/test-classes holds ConfigContractTest.SampleConfig (a @ConfigMapping) and the test descriptor
        ConfigContract contract = ContractExporter.export(Path.of("target/test-classes"),
                Thread.currentThread().getContextClassLoader(), "my-app");

        assertTrue(contract.key("sample.max-amount").orElseThrow().required());
        assertEquals(ConfigContract.Owner.APPLICATION, contract.key("sample.currency").orElseThrow().owner());
        assertEquals(Optional.of("8080"), contract.key("quarkus.http.port").orElseThrow().defaultValue());
        assertTrue(contract.key("test.secret").orElseThrow().secret());
        assertEquals(ConfigContract.Owner.APPLICATION, contract.keys().get(0).owner(), "application keys first");
        assertTrue(contract.keys().get(0).name().startsWith("sample."), "sorted by method name inside the mapping");
        assertTrue(contract.keys().stream().noneMatch(k -> k.name().startsWith("mp.messaging")), "no channels here");
        assertEquals(java.util.List.of("admin", "reader"), contract.roles(), "roles from @RolesAllowed, sorted");
    }

    @jakarta.annotation.security.RolesAllowed("reader")
    static class SecuredSample {

        @jakarta.annotation.security.RolesAllowed({ "admin", "reader" })
        void approve() {
        }
    }

    @Test
    void rolesMappingIsPartOfTheContractOnlyWithASecurityModule() {
        ConfigContract withoutSecurity = ConfigContract.builder().role("admin").build();
        assertTrue(!withoutSecurity.rolesMappingExpected());
        ConfigContract withSecurity = ConfigContract.builder().role("admin").role("reader")
                .platform(ConfigContract.ROLES_MAPPING + "*", "String", false, "group -> roles").build();
        assertTrue(withSecurity.rolesMappingExpected());
        ConfigContract back = ConfigContract.fromJson(withSecurity.toJson());
        assertEquals(withSecurity.roles(), back.roles());
        assertEquals(withSecurity.toJson(), back.toJson());
    }

    @Test
    void descriptorsCarryPhaseConstraintsAndReplacements() {
        PlatformDescriptor jpa = PlatformDescriptor.parse("""
                { "module": "bdi-config-jpa", "keys": [
                    { "name": "quarkus.datasource.jdbc.transactions", "type": "String", "default": "enabled", "phase": "build-time",
                      "values": ["enabled", "xa", "disabled"], "doc": "fixed" },
                    { "name": "quarkus.datasource.jdbc.max-size", "type": "int", "default": "20", "min": "1", "max": "500" } ] }
                """);
        PlatformDescriptor xa = PlatformDescriptor.parse("""
                { "module": "bdi-config-jpa-xa", "keys": [
                    { "name": "quarkus.datasource.jdbc.transactions", "type": "String", "default": "xa", "phase": "build-time", "replaces": true } ] }
                """);
        ConfigContract.Builder builder = ConfigContract.builder();
        jpa.keys().forEach(builder::addIfAbsent);
        xa.keys().forEach(k -> { if (xa.replaces(k)) builder.replace(k); else builder.addIfAbsent(k); });
        ConfigContract contract = builder.build();
        ConfigContract.Key transactions = contract.key("quarkus.datasource.jdbc.transactions").orElseThrow();
        assertEquals(Optional.of("xa"), transactions.defaultValue());
        assertTrue(transactions.buildTime());
        assertEquals(Optional.of("1"), contract.key("quarkus.datasource.jdbc.max-size").orElseThrow().constraints().min());
        ConfigContract back = ConfigContract.fromJson(contract.toJson());
        assertEquals(contract.keys(), back.keys(), "phase and constraints survive the JSON round trip");
        assertTrue(contract.toJson().contains("\"phase\": \"build-time\""));
    }

    @Test
    void channelTemplatesAreSubstituted() {
        PlatformDescriptor.ChannelKey template = new PlatformDescriptor.ChannelKey("topic", "String", false,
                Optional.of("{application}-{channel}"), false, "topic");
        ConfigContract.Key key = template.forChannel("outgoing", "orders-out", "my-app");
        assertEquals("mp.messaging.outgoing.orders-out.topic", key.name());
        assertEquals(Optional.of("my-app-orders-out"), key.defaultValue());
    }

    @Test
    void jsonRoundTrip() {
        ConfigContract contract = ConfigContract.builder()
                .platform("a.b", "String", true, "doc \"quoted\"")
                .platformSecret("a.secret", "s")
                .platform("a.c", "int", "3", "")
                .build();
        ConfigContract back = ConfigContract.fromJson(contract.toJson());
        assertEquals(contract.toJson(), back.toJson());
        assertEquals(contract.keys(), back.keys());
    }
}
