package it.mulders.junk.rabbitmq;

import com.rabbitmq.jms.admin.RMQConnectionFactory;
import it.mulders.junk.rabbitmq.jms.AutoClosableConnection;
import it.mulders.junk.rabbitmq.jms.AutoClosableMessageProducer;
import it.mulders.junk.rabbitmq.jms.AutoClosableSession;
import lombok.extern.slf4j.Slf4j;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.UUID;

@Slf4j
public class RabbitUtil {
    private Context environment;

    public RabbitUtil() throws GenericException {
        try {
            var context = new InitialContext();
            this.environment = (Context) context.lookup("java:comp/env");
        } catch (NamingException ne) {
            log.error("Could not obtain initial JNDI context", ne);
            throw new GenericException(ne);
        }
    }

    public AutoClosableConnection createConnection() throws NamingException, JMSException {
        var connectionFactory = (ConnectionFactory) environment.lookup("jms/ConnectionFactory");
        ((RMQConnectionFactory) connectionFactory).setDeclareReplyToDestination(false);
        var connection = connectionFactory.createConnection();
        connection.start();

        var metadata = connection.getMetaData();
        log.info("Obtained a JMS {}.{} connection with {}",
                metadata.getJMSMajorVersion(), metadata.getJMSMinorVersion(), metadata.getJMSProviderName());

        return new AutoClosableConnection(connection);
    }

    public AutoClosableSession createSession(final Connection connection, final boolean transacted, final int acknowledgeMode) throws GenericException {
        try {
            var session = connection.createSession(transacted, acknowledgeMode);
            return new AutoClosableSession(session);
        } catch (JMSException jmse) {
            log.error("Could not connect to JMS server", jmse);
            throw new GenericException(jmse);
        }
    }

    public AutoClosableMessageProducer createMessageProducer(final Session session, final Destination destination) throws GenericException {
        try {
            var producer = session.createProducer(destination);
            return new AutoClosableMessageProducer(producer);
        } catch (JMSException jmse) {
            log.error("Could not create message producer", jmse);
            throw new GenericException(jmse);
        }
    }

    public Queue findQueueByName(final String queueName) throws GenericException {
        try {
            var queue = (Queue) environment.lookup(queueName);
            log.info("Obtained reference to JMS queue with name {}", queue.getQueueName());
            return queue;
        } catch (NamingException ne) {
            log.error("Could not lookup queue with name {}", queueName, ne);
            throw new GenericException(ne);
        } catch (JMSException jmse) {
            log.error("Could not verify queue name", jmse);
            throw new GenericException(jmse);
        }
    }

    public Message createMessage(final Session session, final Destination replyTo, final byte[] bytes) throws GenericException {
        try {
            var message = session.createBytesMessage();
            message.setJMSExpiration(10_000); // value is in milliseconds
            message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            if (replyTo != null) message.setJMSReplyTo(replyTo);
            message.setJMSCorrelationID(UUID.randomUUID().toString());
            message.writeBytes(bytes);

            return message;
        } catch (JMSException jmse) {
            log.error("Could not create JMS message", jmse);
            throw new GenericException(jmse);
        }
    }

    public Message createMessage(final Session session, final byte[] bytes) throws GenericException {
        return createMessage(session, null, bytes);
    }
}
