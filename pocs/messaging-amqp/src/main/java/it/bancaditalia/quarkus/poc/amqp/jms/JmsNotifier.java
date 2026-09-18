package it.bancaditalia.quarkus.poc.amqp.jms;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.TextMessage;
import it.bancaditalia.quarkus.poc.amqp.config.OrdersConfig;

/**
 * The JMS API variation (design note 4.3, "code that leans on JMS semantics"): a transacted session, a priority,
 * a property the consumer filters on. Same broker, same amqp-* platform keys, the JMS 3.1 API unchanged from EAP.
 */
@ApplicationScoped
public class JmsNotifier {

    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    OrdersConfig config;

    public void notify(String reference, String text, int priority) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED)) {
            TextMessage message = context.createTextMessage(text);
            message.setStringProperty("reference", reference);
            JMSProducer producer = context.createProducer().setPriority(priority);
            producer.send(context.createQueue(config.jms().notificationsQueue()), message);
            context.commit(); // nothing leaves the session before the commit, as on EAP
        } catch (jakarta.jms.JMSException e) {
            throw new IllegalStateException(e);
        }
    }
}
