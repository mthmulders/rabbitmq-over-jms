package it.mulders.junk.rabbitmq.jms;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import javax.jms.Connection;

@AllArgsConstructor
public class AutoClosableConnection implements AutoCloseable, Connection {
    @Delegate
    private final Connection connection;
}
