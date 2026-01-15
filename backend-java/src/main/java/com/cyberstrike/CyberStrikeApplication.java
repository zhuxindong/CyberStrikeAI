package com.cyberstrike;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CyberStrikeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CyberStrikeApplication.class, args);
	}

}
