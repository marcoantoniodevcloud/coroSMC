package org.opensong.web;

import java.awt.Desktop;
import java.net.URI;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class OpenSongWebApplication {

    private static final Logger logger = LoggerFactory.getLogger(OpenSongWebApplication.class);

    private final Environment environment;

    public OpenSongWebApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(OpenSongWebApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openDesktopBrowser() {
        boolean desktopMode = Arrays.asList(environment.getActiveProfiles()).contains("desktop")
                || Arrays.asList(environment.getProperty("opensongweb.desktop", "").split(","))
                .contains("true");
        if (!desktopMode) {
            return;
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            logger.warn("A browser could not be opened automatically. Visit http://localhost:{}",
                    environment.getProperty("local.server.port", "8080"));
            return;
        }

        String port = environment.getProperty("local.server.port", "8080");
        try {
            Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
        } catch (Exception e) {
            logger.error("Unable to open OpenSongWeb in the default browser", e);
        }
    }
}
