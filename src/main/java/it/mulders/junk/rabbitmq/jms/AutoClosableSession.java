package it.mulders.junk.rabbitmq.jms;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import javax.jms.Session;

@AllArgsConstructor
public class AutoClosableSession implements AutoCloseable, Session {
    @Delegate
    private Session session;
}
