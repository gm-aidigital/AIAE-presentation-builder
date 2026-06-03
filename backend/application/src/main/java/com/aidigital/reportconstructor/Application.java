// Spring Boot entrypoint. Generated apps put this in
//   backend/application/src/main/java/com/aidigital/<appname>/Application.java
// Replace com.aidigital.reportconstructor with com.aidigital.<app-name-package>.

package com.aidigital.reportconstructor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed by the runtime
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
