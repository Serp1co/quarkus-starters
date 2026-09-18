package it.bancaditalia.quarkus.poc.kafka.api;

import it.bancaditalia.quarkus.poc.kafka.domain.AccountBalance;
import it.bancaditalia.quarkus.poc.kafka.domain.PaymentEvent;
import it.bancaditalia.quarkus.poc.kafka.messaging.BalanceConsumer;
import it.bancaditalia.quarkus.poc.kafka.messaging.PaymentProducer;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    public record NewPayment(@NotBlank @Size(max = 34) String account, @NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    public record BalanceView(String account, BigDecimal balance, int events, long lastSequence, int outOfOrder) {
        static BalanceView of(AccountBalance b) {
            return new BalanceView(b.getAccount(), b.getBalance(), b.getEvents(), b.getLastSequence(), b.getOutOfOrder());
        }
    }

    @Inject
    PaymentProducer producer;

    @Inject
    BalanceConsumer consumer;

    private final AtomicLong sequence = new AtomicLong();

    /** Produces the event and answers once the broker has acknowledged the record (acks=all by default). */
    @POST
    @Path("/payments")
    public CompletionStage<Map<String, Object>> publish(@Valid NewPayment request) {
        PaymentEvent event = new PaymentEvent(UUID.randomUUID().toString(), request.account(), request.amount(),
                sequence.incrementAndGet());
        return producer.publish(event).thenApply(v -> Map.of("eventId", event.eventId(), "sequence", event.sequence()));
    }

    @GET
    @Path("/balances/{account}")
    public BalanceView balance(@PathParam("account") String account) {
        return consumer.balance(account).map(BalanceView::of).orElseThrow(() -> new NotFoundException(account));
    }

    @GET
    @Path("/dead-letters")
    public List<String> deadLetters() {
        return consumer.deadLetters();
    }
}
