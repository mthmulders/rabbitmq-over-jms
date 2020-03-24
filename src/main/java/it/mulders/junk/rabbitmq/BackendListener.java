package it.mulders.junk.rabbitmq;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Starts a "back end" process that will just reply to every message it receives.
 */
@Slf4j
@WebListener
public class BackendListener implements ServletContextListener {
    private BackendImplementation backend;

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        try {
            this.backend = new BackendImplementation(new RabbitUtil());
            this.backend.start();
        } catch (GenericException ge) {
            log.error("Could not initialize backend", ge);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        if (this.backend != null) {
            this.backend.stop();
        }
    }
}
