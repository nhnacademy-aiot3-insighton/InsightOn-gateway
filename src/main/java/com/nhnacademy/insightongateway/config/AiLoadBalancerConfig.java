package com.nhnacademy.insightongateway.config;

import com.nhnacademy.insightongateway.loadbalancer.ResponseTimeRegistry;
import com.nhnacademy.insightongateway.loadbalancer.WeightResponseTimeLoadBalancer;
import com.nhnacademy.insightongateway.loadbalancer.WeightedInstancePicker;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class AiLoadBalancerConfig {

    @Bean
    public WeightedInstancePicker weightedInstancePicker(ResponseTimeRegistry registry) {
        return new WeightedInstancePicker(registry);
    }

    @Bean
    public ReactorLoadBalancer<ServiceInstance> reactorLoadBalancer(Environment environment,
                                                                    LoadBalancerClientFactory clientFactory,
                                                                    WeightedInstancePicker picker) {
        String serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new WeightResponseTimeLoadBalancer(clientFactory.getLazyProvider(serviceId, ServiceInstanceListSupplier.class), picker);
    }
}
