package it.bancaditalia.quarkus.poc.remote.caller.platform;

import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.util.LinkedHashMap;
import java.util.Map;

/** The mock realm, plus the application's own client (bdi-config-oidc-client keys) for the service identity. */
public class CallerRealm extends RhbkLikeRealm {

    @Override
    public Map<String, String> start() {
        Map<String, String> keys = new LinkedHashMap<>(super.start());
        keys.put("quarkus.oidc-client.auth-server-url", keys.get("quarkus.oidc.auth-server-url"));
        keys.put("quarkus.oidc-client.client-id", "poc-caller");
        keys.put("quarkus.oidc-client.credentials.secret", "poc-caller-secret");
        keys.put("quarkus.oidc-client.tls.verification", "none");
        return keys;
    }
}
