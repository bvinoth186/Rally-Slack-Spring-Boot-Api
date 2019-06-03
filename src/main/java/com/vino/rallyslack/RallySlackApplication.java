package com.vino.rallyslack;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class RallySlackApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(RallySlackApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(RallySlackApplication.class, args);
		LOGGER.info("Application Started " + new Date());
	}
	
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
	   return builder.build();
	}

}
