package it.bancaditalia.quarkus.test.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFReader;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plays Active Directory for a test: an in-memory directory without schema checks, so that AD object classes
 * ({@code user}, {@code group}) and attributes ({@code sAMAccountName}, {@code userPrincipalName},
 * {@code memberOf}, {@code member}) load as they are exported from a real domain. Users bind with their
 * {@code userPassword}. Returns the platform keys of bdi-config-security-ldap: URL, service account, base DNs.
 * <p>
 * Directory content: {@code /ad-directory.ldif} on the test classpath if present, else the bundled default
 * ({@link #DEFAULT_LDIF}: alice in APP-ADMINS, bob in APP-OPERATORS, carol in APP-READERS, dave in no group,
 * password {@code <name>-pw}).
 */
public class AdLikeDirectory implements QuarkusTestResourceLifecycleManager {

    public static final String BASE_DN = "DC=bancaditalia,DC=it";
    public static final String USERS_DN = "OU=Users," + BASE_DN;
    public static final String GROUPS_DN = "OU=Groups," + BASE_DN;
    public static final String SERVICE_ACCOUNT_DN = "CN=svc-app,OU=Service Accounts," + BASE_DN;
    public static final String SERVICE_ACCOUNT_PASSWORD = "svc-app-pw";
    public static final String CUSTOM_LDIF = "/ad-directory.ldif";
    public static final String DEFAULT_LDIF = "/bdi-ad-default.ldif";

    private InMemoryDirectoryServer server;

    @Override
    public Map<String, String> start() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
            config.setSchema(null); // AD attributes are not in the standard schema
            config.addAdditionalBindCredentials(SERVICE_ACCOUNT_DN, SERVICE_ACCOUNT_PASSWORD);
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0));
            server = new InMemoryDirectoryServer(config);
            try (InputStream ldif = ldif()) {
                server.importFromLDIF(true, new LDIFReader(ldif));
            }
            server.startListening();
        } catch (LDAPException e) {
            throw new IllegalStateException("cannot start the AD look-alike", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return platformKeys();
    }

    /** The keys the platform would render for this directory (bdi-config-security-ldap's contract). */
    public Map<String, String> platformKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("quarkus.security.ldap.dir-context.url", url());
        keys.put("quarkus.security.ldap.dir-context.principal", SERVICE_ACCOUNT_DN);
        keys.put("quarkus.security.ldap.dir-context.password", SERVICE_ACCOUNT_PASSWORD);
        keys.put("quarkus.security.ldap.identity-mapping.search-base-dn", USERS_DN);
        keys.put("quarkus.security.ldap.identity-mapping.attribute-mappings.groups.filter-base-dn", GROUPS_DN);
        return keys;
    }

    public String url() {
        return "ldap://localhost:" + server.getListenPort();
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.shutDown(true);
            server = null;
        }
    }

    private static InputStream ldif() {
        InputStream custom = AdLikeDirectory.class.getResourceAsStream(CUSTOM_LDIF);
        if (custom != null) {
            return custom;
        }
        InputStream bundled = AdLikeDirectory.class.getResourceAsStream(DEFAULT_LDIF);
        if (bundled == null) {
            throw new IllegalStateException(DEFAULT_LDIF + " missing from bdi-test-ldap");
        }
        return bundled;
    }
}
