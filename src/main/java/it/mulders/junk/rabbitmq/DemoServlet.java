package it.mulders.junk.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.Charset.defaultCharset;

/**
 * Provides a way to publish a message to RabbitMQ using the JMS API.
 */
@Slf4j
@WebServlet(urlPatterns = "/")
public class DemoServlet extends HttpServlet {
    private RabbitUtil rabbitUtil;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        try {
            this.rabbitUtil = new RabbitUtil();
        } catch (GenericException ge) {
            throw new ServletException(ge);
        }
    }

    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        try {
            var reply = performRequestReply();

            if (reply != null) {
                response.getWriter().write("Well, that seemed to work!\n\n");
                response.getWriter().write(reply.data + "\n\n");
                response.getWriter().write("(received in " + reply.duration + "ms)");
            } else {
                response.getWriter().write("Well, that didn't seem to work!\n\nNo reply was received");
            }
        } catch (GenericException ge) {
            var writer = response.getWriter();
            writer.write("Ouch, no such luck! \nAn exception was thrown. This is the stacktrace: \n\n");
            ge.printStackTrace(writer);
        }
    }

    @AllArgsConstructor
    @Getter
    static class Response<T> {
        private final long duration;
        private final T data;
    }

    private Response<String> performRequestReply() throws GenericException {
        var responses = new ArrayBlockingQueue<Message>(1);

        try (var connection = this.rabbitUtil.createConnection();
             var session = this.rabbitUtil.createSession(connection, false, Session.AUTO_ACKNOWLEDGE)) {

            var queue = this.rabbitUtil.findQueueByName("jms/ExampleQueue");

            var replyQueue = session.createTemporaryQueue();

            var responseConsumer = session.createConsumer(replyQueue);
            responseConsumer.setMessageListener(responses::add);

            var message = this.rabbitUtil.createMessage(session, replyQueue, "Hello, world".getBytes(defaultCharset()));

            var producer = session.createProducer(queue);
            producer.setTimeToLive(60000); // value is in milliseconds

            producer.send(message);

            var start = System.currentTimeMillis();
            var r = ((BytesMessage) responses.poll(2, TimeUnit.SECONDS));
            var end = System.currentTimeMillis();

            replyQueue.delete();
            responseConsumer.close();

            if (r != null) {
                var bytes = new byte[(int) r.getBodyLength()];
                r.readBytes(bytes, (int) r.getBodyLength());

                return new Response<>(end - start, new String(bytes));
            }
        } catch (NamingException ne) {
            log.error("Lookup problems", ne);
            throw new GenericException(ne);
        } catch (JMSException jmse) {
            log.error("Problem working with the JMS server", jmse);
            throw new GenericException(jmse);
        } catch (InterruptedException ie) {
            log.error("Interrupted while waiting for a response to come in", ie);
            throw new GenericException(ie);
        }

        return null;
    }
}
