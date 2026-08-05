package com.allygo.allygo_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AllygoApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AllygoApiApplication.class, args);
	}

}
