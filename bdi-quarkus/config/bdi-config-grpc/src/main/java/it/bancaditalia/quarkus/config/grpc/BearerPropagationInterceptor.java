package it.bancaditalia.quarkus.config.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.quarkus.arc.Arc;
import io.quarkus.grpc.GlobalInterceptor;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Caller-identity propagation for gRPC (design note 4.2): the bearer token of the current request, if any,
 * travels in the {@code authorization} metadata of every outbound gRPC call, as
 * {@code quarkus-rest-client-oidc-token-propagation} does for REST. Outside a request (a timer, a consumer)
 * nothing is added: a service identity is the OIDC client's job, not a user's token replayed.
 */
@GlobalInterceptor
@ApplicationScoped
public class BearerPropagationInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions options, Channel next) {
        String token = currentToken();
        ClientCall<Q, R> call = next.newCall(method, options);
        if (token == null) {
            return call;
        }
        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<R> listener, Metadata headers) {
                if (headers.get(AUTHORIZATION) == null) {
                    headers.put(AUTHORIZATION, "Bearer " + token);
                }
                super.start(listener, headers);
            }
        };
    }

    private static String currentToken() {
        if (!Arc.container().requestContext().isActive()) {
            return null;
        }
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        TokenCredential credential = identity.getCredential(TokenCredential.class);
        return credential == null ? null : credential.getToken();
    }
}
