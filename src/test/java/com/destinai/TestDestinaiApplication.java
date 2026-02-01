package com.destinai;

import org.springframework.boot.SpringApplication;

public class TestDestinaiApplication {

	public static void main(String[] args) {
		SpringApplication.from(DestinaiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
