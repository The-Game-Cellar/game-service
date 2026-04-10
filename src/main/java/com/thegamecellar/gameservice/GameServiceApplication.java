package com.thegamecellar.gameservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class GameServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(GameServiceApplication.class);

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(GameServiceApplication.class, args);
        // TODO: nedan är för development
        String port = ctx.getEnvironment().getProperty("server.port", "8081");
        log.info("-------------------------------------------------------");
        log.info("  Game Service is UP and running!");
        log.info("  http://localhost:{}/api/v1/games", port);
        log.info("-------------------------------------------------------");
    }

}