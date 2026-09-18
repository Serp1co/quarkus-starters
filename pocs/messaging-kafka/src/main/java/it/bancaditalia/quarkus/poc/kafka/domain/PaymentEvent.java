package it.bancaditalia.quarkus.poc.kafka.domain;

import java.math.BigDecimal;

/** The record on the topic: JSON, keyed by account so that one account's events keep their order. */
public record PaymentEvent(String eventId, String account, BigDecimal amount, long sequence) {
}
