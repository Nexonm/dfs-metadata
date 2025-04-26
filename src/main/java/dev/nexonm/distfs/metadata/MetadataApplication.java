package dev.nexonm.distfs.metadata;

import dev.nexonm.distfs.metadata.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
		FileStorageProperties.class
})
@EnableScheduling
public class MetadataApplication {

	public static void main(String[] args) {
		SpringApplication.run(MetadataApplication.class, args);
	}

}
