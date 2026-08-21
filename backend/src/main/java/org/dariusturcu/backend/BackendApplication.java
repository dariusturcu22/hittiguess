package org.dariusturcu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class BackendApplication {
    static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
        IO.println("Hello world");
    }
}
