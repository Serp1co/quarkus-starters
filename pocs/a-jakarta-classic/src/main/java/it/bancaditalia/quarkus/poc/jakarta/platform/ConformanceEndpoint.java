package it.bancaditalia.quarkus.poc.jakarta.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.vertx.http.ManagementInterface;
import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * {@code GET /q/platform} on the management port: the post-deploy conformance check of the design notes
 * (health, version, config-source echo) so that "same artifact, same contract" is a test on every stage.
 * It reports, for each contract key, which config source served it and whether required keys are missing.
 * Secrets are masked. It sits on the management interface, which the platform firewalls to ops and the LB.
 */
@ApplicationScoped
public class ConformanceEndpoint {

    @Inject
    ConfigContract contract;

    @Inject
    Config config;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version")
    String applicationVersion;

    /** The router of the management interface is rooted at "/", so the route is placed under the management root path. */
    @ConfigProperty(name = "quarkus.management.root-path", defaultValue = "/q")
    String managementRootPath;

    void register(@Observes ManagementInterface management) {
        String root = managementRootPath.startsWith("/") ? managementRootPath : "/" + managementRootPath;
        String path = (root.endsWith("/") ? root : root + "/") + "platform";
        management.router().get(path).handler(context -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("application", Map.of(
                    "name", applicationName,
                    "version", applicationVersion,
                    "profiles", ConfigUtils.getProfiles()));
            body.put("missing", contract.missing(config));
            body.put("config", contract.echo(config));
            try {
                context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(mapper.writeValueAsString(body));
            } catch (JsonProcessingException e) {
                context.fail(e);
            }
        });
    }
}
