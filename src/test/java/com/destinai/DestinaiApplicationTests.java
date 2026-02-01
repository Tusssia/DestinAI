package com.destinai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class DestinaiApplicationTests {

	@Test
	void contextLoads() {
	}

}
