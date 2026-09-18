package it.bancaditalia.quarkus.poc.amqp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.bancaditalia.quarkus.poc.amqp.domain.OrderEvent;
import it.bancaditalia.quarkus.poc.amqp.domain.OutboxMessage;
import it.bancaditalia.quarkus.poc.amqp.domain.PaymentOrder;
import it.bancaditalia.quarkus.poc.amqp.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Optional;

@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository orders;

    @Inject
    ObjectMapper mapper;

    /** One local transaction: the order and its outbox event, or neither. No XA, no lost event. */
    @Transactional
    public PaymentOrder submit(String reference, BigDecimal amount) {
        PaymentOrder order = new PaymentOrder(reference, amount);
        orders.persist(order);
        orders.persist(new OutboxMessage("PaymentOrder", reference, toJson(new OrderEvent(reference, amount))));
        return order;
    }

    public Optional<PaymentOrder> find(String reference) {
        return orders.findByReference(reference);
    }

    private String toJson(OrderEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
