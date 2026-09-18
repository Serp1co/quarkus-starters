package it.bancaditalia.quarkus.poc.amqp.domain;

import java.math.BigDecimal;

/** The wire event: JSON on the AMQP message body. The message id is the outbox id. */
public record OrderEvent(String reference, BigDecimal amount) {
}
