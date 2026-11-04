package com.venuepass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VenuePassApplication {
    public static void main(String[] args) { SpringApplication.run(VenuePassApplication.class, args); }
}
