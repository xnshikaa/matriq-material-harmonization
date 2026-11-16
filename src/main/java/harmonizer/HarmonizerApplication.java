package harmonizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. If the project already has a @SpringBootApplication class, keep only one. */
@SpringBootApplication
public class HarmonizerApplication {
    public static void main(String[] args) {
        SpringApplication.run(HarmonizerApplication.class, args);
    }
}
