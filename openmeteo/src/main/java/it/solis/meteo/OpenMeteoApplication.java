package it.solis.meteo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpenMeteoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenMeteoApplication.class, args);
    }
}
