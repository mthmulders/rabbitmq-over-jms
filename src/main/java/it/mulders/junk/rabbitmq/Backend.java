package it.mulders.junk.rabbitmq;

import com.rabbitmq.jms.admin.RMQConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.nio.charset.Charset;

/**
 * Starts a "back end" process that will just reply to every message it receives.
 */
@Slf4j
@WebListener
public class Backend implements ServletContextListener {
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private MessageProducer producer;

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        try {
            var context = new InitialContext();
            var environment = (Context) context.lookup("java:comp/env");

            var connectionFactory = (ConnectionFactory) environment.lookup("jms/ConnectionFactory");
            ((RMQConnectionFactory)connectionFactory).setDeclareReplyToDestination(false);

            this.connection = connectionFactory.createConnection();
            this.connection.setClientID("consumer");
            this.connection.start();
            var metadata = connection.getMetaData();
            log.info("Obtained a JMS {}.{} connection with {}",
                    metadata.getJMSMajorVersion(), metadata.getJMSMinorVersion(), metadata.getJMSProviderName());

            this.session = connection.createSession(true, 4);

            var queue = (Queue) environment.lookup("jms/ExampleQueue");

            this.producer = this.session.createProducer(null);

            this.consumer = session.createConsumer(queue);
            this.consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(final Message message) {
                    try {
                        log.info("Processing message {} on queue {}", message.getJMSCorrelationID(), queue.getQueueName());
                        Destination replyQueue = message.getJMSReplyTo();
                        log.info("Will respond to queue {}", replyQueue);

                        var response = session.createBytesMessage();
                        response.writeBytes("Hello to you, too!".getBytes(Charset.defaultCharset()));

                        producer.send(replyQueue, response);
                    } catch (JMSException e) {
                        log.error("Could not process message", e);
                    }
                }
            });
        } catch (JMSException | NamingException e) {
            log.error("Could not initialize backend", e);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        if (this.consumer != null) {
            try {
                this.consumer.close();
            } catch (JMSException e) {
                log.error("Failed to close JMS consumer", e);
            }
        }
        if (this.producer != null) {
            try {
                this.producer.close();
            } catch (JMSException e) {
                log.error("Failed to close JMS producer", e);
            }
        }
        if (this.session != null) {
            try {
                this.session.close();
            } catch (JMSException e) {
                log.error("Failed to close JMS session", e);
            }
        }
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (JMSException e) {
                log.error("Failed to close JMS connection", e);
            }
        }
    }
}
