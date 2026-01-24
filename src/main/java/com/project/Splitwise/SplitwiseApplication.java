package com.project.Splitwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {
        "com.project.Splitwise.model",
        "com.project.Splitwise.readmodel",
        "com.project.Splitwise.dlq"
})
public class SplitwiseApplication {

	public static void main(String[] args) {
		SpringApplication.run(SplitwiseApplication.class, args);
	}

}
