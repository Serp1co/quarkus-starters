package it.bancaditalia.quarkus.poc.amqp.jms;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.TextMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The JMS API variation (design note 4.3, "code that leans on JMS semantics"): a transacted session, a priority,
 * a property the consumer filters on. Same broker, same amqp-* platform keys, the JMS 3.1 API unchanged from EAP.
 */
@ApplicationScoped
public class JmsNotifier {

    @Inject
    ConnectionFactory connectionFactory;

    @ConfigProperty(name = "orders.jms.notifications-queue", defaultValue = "orders.notifications")
    String queue;

    public void notify(String reference, String text, int priority) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED)) {
            TextMessage message = context.createTextMessage(text);
            message.setStringProperty("reference", reference);
            JMSProducer producer = context.createProducer().setPriority(priority);
            producer.send(context.createQueue(queue), message);
            context.commit(); // nothing leaves the session before the commit, as on EAP
        } catch (jakarta.jms.JMSException e) {
            throw new IllegalStateException(e);
        }
    }
}
