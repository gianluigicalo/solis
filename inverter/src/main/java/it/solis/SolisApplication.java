package it.solis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SolisApplication {

    public static void main(String[] args) {
        // Netty 4.1 (usato da modbus-master-tcp) chiama sun.misc.Unsafe, deprecato da Java 24 (JEP 498):
        // senza questa proprietà la JVM stampa dei warning. Va impostata prima che Netty venga caricato.
        System.setProperty("io.netty.noUnsafe", "true");
        SpringApplication.run(SolisApplication.class, args);
    }
}
