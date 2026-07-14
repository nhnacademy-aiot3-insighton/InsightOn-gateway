package com.nhnacademy.insightongateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class InsightonGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightonGatewayApplication.class, args);
    }

}
