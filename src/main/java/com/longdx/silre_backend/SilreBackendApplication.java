package com.longdx.silre_backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SilreBackendApplication {

	private static final Logger logger = LoggerFactory.getLogger(SilreBackendApplication.class);

	public static void main(String[] args) {
		logger.info("CHECK JWT KEY : " + (System.getenv("JWT_SECRET") != null ? "Existed" : "Null"));
		logger.info("CHECK DB URL : " + (System.getenv("DB_URL") != null ? "Existed"	: "Null"));
		logger.info("CHECK REDIS PORT : " + (System.getenv("REDIS_PORT") != null ? "Existed" : "Null"));
		SpringApplication.run(SilreBackendApplication.class, args);
	}

}
