package it.mulders.junk.rabbitmq.jms;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import javax.jms.MessageProducer;

@AllArgsConstructor
public class AutoClosableMessageProducer implements AutoCloseable, MessageProducer {
    @Delegate
    private final MessageProducer producer;
}
