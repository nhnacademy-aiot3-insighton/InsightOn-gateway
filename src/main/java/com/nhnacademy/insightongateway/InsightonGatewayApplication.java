package com.nhnacademy.insightongateway;

import com.nhnacademy.insightongateway.config.AiLoadBalancerConfig;
import com.nhnacademy.insightongateway.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = AiLoadBalancerConfig.class
))
@LoadBalancerClient(value = "insighton-ai", configuration = AiLoadBalancerConfig.class)
public class InsightonGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightonGatewayApplication.class, args);
    }
}
