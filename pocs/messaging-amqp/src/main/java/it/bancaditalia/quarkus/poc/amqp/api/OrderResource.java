package it.bancaditalia.quarkus.poc.amqp.api;

import it.bancaditalia.quarkus.poc.amqp.domain.PaymentOrder;
import it.bancaditalia.quarkus.poc.amqp.jms.JmsNotificationListener;
import it.bancaditalia.quarkus.poc.amqp.jms.JmsNotifier;
import it.bancaditalia.quarkus.poc.amqp.messaging.OrderConsumer;
import it.bancaditalia.quarkus.poc.amqp.messaging.OutboxRelay;
import it.bancaditalia.quarkus.poc.amqp.service.OrderService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    public record NewOrder(@NotBlank @Size(max = 35) String reference,
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    public record OrderView(String reference, BigDecimal amount, String status, Instant createdAt, Instant processedAt,
            int deliveries) {
        static OrderView of(PaymentOrder o) {
            return new OrderView(o.getReference(), o.getAmount(), o.getStatus().name(), o.getCreatedAt(), o.getProcessedAt(),
                    o.getDeliveries());
        }
    }

    @Inject
    OrderService orders;

    @Inject
    OrderConsumer consumer;

    @Inject
    OutboxRelay relay;

    @Inject
    JmsNotifier notifier;

    @Inject
    JmsNotificationListener listener;

    @POST
    @Path("/orders")
    public Response submit(@Valid NewOrder request) {
        PaymentOrder order = orders.submit(request.reference(), request.amount());
        return Response.status(Response.Status.ACCEPTED).entity(OrderView.of(order)).build();
    }

    @GET
    @Path("/orders/{reference}")
    public OrderView get(@PathParam("reference") String reference) {
        return orders.find(reference).map(OrderView::of).orElseThrow(() -> new NotFoundException(reference));
    }

    /** POC hook: republish an outbox row to show the idempotent consumer. */
    @POST
    @Path("/outbox/{id}/resend")
    @Consumes(MediaType.WILDCARD)
    public Map<String, Object> resend(@PathParam("id") String id) {
        return Map.of("resent", relay.resend(id));
    }

    @GET
    @Path("/consumer")
    public Map<String, Object> consumer() {
        return Map.of("deliveries", consumer.deliveries(), "duplicates", consumer.duplicates(), "failures", consumer.failures());
    }

    @POST
    @Path("/notifications")
    @Consumes(MediaType.WILDCARD)
    public Map<String, Object> notify(@QueryParam("reference") String reference, @QueryParam("text") String text,
            @QueryParam("priority") Integer priority) {
        notifier.notify(reference, text, priority == null ? 4 : priority);
        return Map.of("sent", true);
    }

    @GET
    @Path("/notifications")
    public Map<String, Object> notifications() {
        return Map.of("received", listener.received(), "redeliveries", listener.redeliveries());
    }
}
