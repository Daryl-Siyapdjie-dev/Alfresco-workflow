package com.kimia.bcrg_integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.kimia.bcrg_integration.config.BcrgApiProperties;

@SpringBootApplication
@EnableConfigurationProperties(BcrgApiProperties.class)
public class BcrgIntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(BcrgIntegrationApplication.class, args);
	}

}
