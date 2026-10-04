package com.labmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LabMarketplaceApplication {

  public static void main(String[] args) {
    SpringApplication.run(LabMarketplaceApplication.class, args);
  }
}
