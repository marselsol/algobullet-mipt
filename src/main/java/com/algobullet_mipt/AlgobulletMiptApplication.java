package com.algobullet_mipt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlgobulletMiptApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlgobulletMiptApplication.class, args);
	}

}
