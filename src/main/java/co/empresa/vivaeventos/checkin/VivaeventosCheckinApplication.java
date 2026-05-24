package co.empresa.vivaeventos.checkin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VivaeventosCheckinApplication {

    public static void main(String[] args) {
        SpringApplication.run(VivaeventosCheckinApplication.class, args);
    }
}
