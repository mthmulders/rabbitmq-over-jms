package it.mulders.junk.rabbitmq;

import lombok.extern.slf4j.Slf4j;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Provides a way to publish a message to RabbitMQ using the JMS API.
 */
@Slf4j
@WebServlet(urlPatterns = "/")
public class DemoServlet extends HttpServlet {
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        try {
            var context = new InitialContext();
            var environment = (Context) context.lookup("java:comp/env");

            var connectionFactory = (ConnectionFactory) environment.lookup("jms/ConnectionFactory");
            this.connection = connectionFactory.createConnection();
            this.connection.start();
            var metadata = connection.getMetaData();
            log.info("Obtained a JMS {}.{} connection with {}",
                    metadata.getJMSMajorVersion(), metadata.getJMSMinorVersion(), metadata.getJMSProviderName());

            this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            var queue = (Queue) environment.lookup("jms/ExampleQueue");
            log.info("Obtained reference to JMS queue with name {}", queue.getQueueName());

            this.producer = this.session.createProducer(queue);
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

    protected void doGet(final HttpServletRequest request,
                         final HttpServletResponse response)
            throws ServletException, IOException
    {
        try {
            var replyQueue = session.createTemporaryQueue();
            var replyQueueName = replyQueue.getQueueName();
            producer.setTimeToLive(60000); // value is in milliseconds
            var message = this.session.createBytesMessage();
            message.setJMSExpiration(10_000); // value is in milliseconds

            message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            message.setJMSReplyTo(replyQueue);
            message.setJMSCorrelationID(UUID.randomUUID().toString());
            message.writeBytes("Hello, world".getBytes(Charset.defaultCharset()));

            var responseConsumer = session.createConsumer(replyQueue);

            producer.send(message);

            var responses = new ArrayBlockingQueue<Message>(1);
            responseConsumer.setMessageListener(responses::add);

            var r = ((BytesMessage) responses.poll(2, TimeUnit.SECONDS));
            responseConsumer.close();
            if (r != null) {
                var bytes = new byte[(int) r.getBodyLength()];
                r.readBytes(bytes, (int) r.getBodyLength());

                response.getWriter().write(
                        "Well, that seemed to work! \n"
                                + "Message was sent with reply-to " + replyQueueName + ".\n\n"
                                + "Reply was [" + new String(bytes) + "]");
            } else {
                response.getWriter().write(
                        "Well, that didn't seem to work! \n"
                                + "Message was sent with reply-to " + replyQueueName + " \n\n"
                                + "No reply was received");
            }

            replyQueue.delete();

        } catch (JMSException | InterruptedException e) {
            throw new ServletException(e);
        }
    }
}
