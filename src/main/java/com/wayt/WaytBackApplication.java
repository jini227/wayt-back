package com.wayt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WaytBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaytBackApplication.class, args);
	}

}
