package com.nhnacademy.insightongateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

import java.util.List;

public interface InstancePickStrategy {
    ServiceInstance pick(List<ServiceInstance> instances);
}
