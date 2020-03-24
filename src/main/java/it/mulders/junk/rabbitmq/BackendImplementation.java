package it.mulders.junk.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.NamingException;

import static java.nio.charset.Charset.defaultCharset;

@AllArgsConstructor
@Slf4j
public class BackendImplementation {
    private final RabbitUtil rabbitUtil;

    public BackendImplementation(final RabbitUtil rabbitUtil) {
        this.rabbitUtil = rabbitUtil;
    }

    private Connection connection;
    private Session session;
    private Queue queue;
    private MessageConsumer consumer;

    public void start() {
        try {
            this.connection = rabbitUtil.createConnection();
            this.session = rabbitUtil.createSession(connection, false, Session.AUTO_ACKNOWLEDGE);
            this.queue = rabbitUtil.findQueueByName("jms/ExampleQueue");
            this.consumer = session.createConsumer(queue);
            consumer.setMessageListener(new DemoMessageListener(rabbitUtil, queue.getQueueName()));
        } catch (NamingException | JMSException | GenericException ge) {
            log.error("Could not initialise message listener", ge);
        }
    }

    @AllArgsConstructor
    static class DemoMessageListener implements MessageListener {
        private final RabbitUtil rabbitUtil;
        private final String incomingQueueName;

        @Override
        public void onMessage(final Message message) {
            try (var connection = rabbitUtil.createConnection();
                 var session = rabbitUtil.createSession(connection, false, Session.AUTO_ACKNOWLEDGE);
                 var producer = rabbitUtil.createMessageProducer(session, null)) {
                log.info("Processing message {} on queue {}", message.getJMSCorrelationID(), incomingQueueName);
                var replyQueue = (Queue) message.getJMSReplyTo();
                log.info("Will respond to queue {}", replyQueue);

                var content = "Hello to you, too!\n" +
                                "Your message was received through " + incomingQueueName +
                                " and it is answered through " + replyQueue.getQueueName() + ".";
                var response = rabbitUtil.createMessage(session, content.getBytes(defaultCharset()));

                producer.send(replyQueue, response);
            } catch (NamingException | GenericException | JMSException e) {
                log.error("Could not process message", e);
            }
        }
    }

    public void stop() {
        try {
            if (this.consumer != null) this.consumer.close();
            if (this.session != null) this.session.close();
            if (this.connection != null) this.connection.close();
        } catch (final JMSException jmse) {
            log.error("Could not properly terminate backend", jmse);
        }
    }
}
