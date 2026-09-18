package it.bancaditalia.quarkus.config.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.spi.runtime.AbstractSecurityEvent;
import io.quarkus.security.spi.runtime.AuthenticationFailureEvent;
import io.quarkus.security.spi.runtime.AuthenticationSuccessEvent;
import io.quarkus.security.spi.runtime.AuthorizationFailureEvent;
import io.quarkus.security.spi.runtime.AuthorizationSuccessEvent;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import java.util.TreeSet;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

/**
 * The audit trail of design note 4.5: every authentication and authorization outcome as one structured log
 * line under {@code it.bancaditalia.security.audit}, which the JSON console handler ships to the SIEM with the
 * user, roles, method, path and outcome as MDC fields. Successful authorizations are logged at DEBUG (one per
 * request is noise on a busy API); failures at WARN. The same lines for LDAP and OIDC identities.
 */
@ApplicationScoped
public class SecurityAuditLogger {

    private static final Logger LOG = Logger.getLogger("it.bancaditalia.security.audit");

    void authenticated(@Observes AuthenticationSuccessEvent event) {
        log(event, "authentication", "success", null, false);
    }

    void authenticationFailed(@Observes AuthenticationFailureEvent event) {
        Throwable cause = event.getAuthenticationFailure();
        log(event, "authentication", "failure", cause == null ? null : cause.getClass().getSimpleName(), true);
    }

    void authorized(@ObservesAsync AuthorizationSuccessEvent event) {
        if (LOG.isDebugEnabled()) {
            log(event, "authorization", "success", event.getEventProperties().containsKey(AuthorizationSuccessEvent.AUTHORIZATION_CONTEXT)
                    ? String.valueOf(event.getEventProperties().get(AuthorizationSuccessEvent.AUTHORIZATION_CONTEXT)) : null, false);
        }
    }

    void authorizationFailed(@Observes AuthorizationFailureEvent event) {
        log(event, "authorization", "failure", event.getAuthorizationContext(), true);
    }

    private static void log(AbstractSecurityEvent event, String kind, String outcome, String detail, boolean warn) {
        SecurityIdentity identity = event.getSecurityIdentity();
        String user = identity == null || identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
        String roles = identity == null ? "" : String.join(",", new TreeSet<>(identity.getRoles()));
        Object context = event.getEventProperties().get(RoutingContext.class.getName());
        String method = "", path = "", remote = "";
        if (context instanceof RoutingContext routing) {
            method = routing.request().method().name();
            path = routing.normalizedPath();
            remote = routing.request().remoteAddress() == null ? "" : routing.request().remoteAddress().hostAddress();
        }
        try {
            MDC.put("audit.kind", kind);
            MDC.put("audit.outcome", outcome);
            MDC.put("audit.user", user);
            MDC.put("audit.roles", roles);
            MDC.put("audit.method", method);
            MDC.put("audit.path", path);
            MDC.put("audit.remote", remote);
            String line = String.format("%s %s user=%s roles=[%s] %s %s remote=%s%s", kind, outcome, user, roles, method, path,
                    remote, detail == null ? "" : " detail=" + detail);
            if (warn) {
                LOG.warn(line);
            } else if ("authentication".equals(kind)) {
                LOG.info(line);
            } else {
                LOG.debug(line);
            }
        } finally {
            for (String key : new String[] { "audit.kind", "audit.outcome", "audit.user", "audit.roles", "audit.method", "audit.path", "audit.remote" }) {
                MDC.remove(key);
            }
        }
    }
}
