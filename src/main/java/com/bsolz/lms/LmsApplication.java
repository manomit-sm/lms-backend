package com.bsolz.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "LMS", sharedModules = "shared")
@SpringBootApplication
public class LmsApplication {

    static void main(String[] args) {
		SpringApplication.run(LmsApplication.class, args);
	}

}
