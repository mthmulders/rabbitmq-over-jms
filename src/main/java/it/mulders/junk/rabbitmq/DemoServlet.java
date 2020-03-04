package it.mulders.junk.rabbitmq;

import lombok.extern.slf4j.Slf4j;

import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Provides a way to publish a message to RabbitMQ using the JMS API.
 */
@Slf4j
@WebServlet(urlPatterns = "/")
public class DemoServlet extends HttpServlet {
    private Session session;
    private Queue queue;
    private MessageProducer producer;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        try {
            var context = new InitialContext();
            var environment = (Context) context.lookup("java:comp/env");

            var connectionFactory = (ConnectionFactory) environment.lookup("jms/ConnectionFactory");
            var connection = connectionFactory.createConnection();
            connection.setClientID("producer");
            var metadata = connection.getMetaData();
            log.info("Obtained a JMS {}.{} connection with {}",
                    metadata.getJMSMajorVersion(), metadata.getJMSMinorVersion(), metadata.getJMSProviderName());

            this.session = connection.createSession(true, 4);

            this.queue = (Queue) environment.lookup("jms/ExampleQueue");
            log.info("Obtained reference to JMS queue with name {}", this.queue.getQueueName());

            this.producer = this.session.createProducer(this.queue);
        } catch (JMSException | NamingException e) {
            throw new ServletException("Could not initialize DemoServlet", e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();

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
                log.error("Failed to close JMS connection", e);
            }
        }
    }

    protected void doGet(final HttpServletRequest request,
                         final HttpServletResponse response)
            throws ServletException, IOException
    {
        try {
            var replyQueue = session.createTemporaryQueue();
            producer.setTimeToLive(60_000); // assuming this is msec from now
            var message = this.session.createBytesMessage();

            message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            message.setJMSReplyTo(replyQueue);
            message.setJMSCorrelationID(UUID.randomUUID().toString());
            message.writeBytes("Hello, world".getBytes(Charset.defaultCharset()));

            producer.send(message);

        } catch (JMSException e) {
            throw new ServletException(e);
        }
        response.getWriter().write("Well, that seemed to work!");
    }
}
