package it.bancaditalia.quarkus.poc.amqp.jms;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * A JMS consumer with a selector on a transacted session: what an MDB with an activation-config selector did.
 * A failure rolls the session back and the broker redelivers ({@code JMSRedelivered}); the poll loop is the
 * container's, written by hand: on Quarkus there is no MDB container, and Reactive Messaging is the preferred shape.
 */
@ApplicationScoped
public class JmsNotificationListener {

    private static final Logger LOG = Logger.getLogger(JmsNotificationListener.class);

    @Inject
    ConnectionFactory connectionFactory;

    @ConfigProperty(name = "orders.jms.notifications-queue", defaultValue = "orders.notifications")
    String queue;

    @ConfigProperty(name = "orders.jms.min-priority", defaultValue = "5")
    int minPriority;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "jms-notifications"));
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();
    private final AtomicInteger redeliveries = new AtomicInteger();

    void onStart(@Observes StartupEvent event) {
        running.set(true);
        executor.submit(this::loop);
    }

    void onStop(@Observes ShutdownEvent event) {
        running.set(false);
        executor.shutdownNow();
    }

    private void loop() {
        while (running.get()) {
            try (JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
                    JMSConsumer consumer = context.createConsumer(context.createQueue(queue), "JMSPriority >= " + minPriority)) {
                while (running.get()) {
                    Message message = consumer.receive(1000);
                    if (message == null) {
                        continue;
                    }
                    try {
                        handle(message);
                        context.commit();
                    } catch (RuntimeException e) {
                        LOG.warnv("notification rolled back for redelivery: {0}", e.getMessage());
                        context.rollback();
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    LOG.warnv("JMS listener reconnecting: {0}", e.getMessage());
                    sleep();
                }
            }
        }
    }

    private void handle(Message message) throws RuntimeException {
        try {
            if (message.getJMSRedelivered()) {
                redeliveries.incrementAndGet();
            }
            String text = message.getBody(String.class);
            if (text.startsWith("POISON") && !message.getJMSRedelivered()) {
                throw new IllegalStateException("first delivery of " + text + " fails on purpose");
            }
            received.add(message.getStringProperty("reference") + ": " + text + " (priority " + message.getJMSPriority()
                    + (message.getJMSRedelivered() ? ", redelivered)" : ")"));
        } catch (jakarta.jms.JMSException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<String> received() {
        return List.copyOf(received);
    }

    public int redeliveries() {
        return redeliveries.get();
    }
}
