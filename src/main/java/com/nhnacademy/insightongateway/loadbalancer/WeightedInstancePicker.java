package com.nhnacademy.insightongateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

import java.security.Provider;
import java.util.List;
import java.util.Random;

public class WeightedInstancePicker {

    private final ResponseTimeRegistry registry;
    private final Random random = new Random();

    public WeightedInstancePicker(ResponseTimeRegistry registry) {
        this.registry = registry;
    }

    public ServiceInstance pick(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.getFirst();
        }

        double[] weights = calculateWeights(instances);
        return pickByWeight(instances, weights);
    }

    private double[] calculateWeights(List<ServiceInstance> instances) {
        double[] weights = new double[instances.size()];
        double[] rawResponseTimes = new double[instances.size()];

        double sumKnown = 0;
        int knownCount = 0;

        for (int i = 0; i < instances.size(); i++) {
            String key = instancesKey(instances.get(i));
            double avg = registry.getAverage(key);
            rawResponseTimes[i] = avg;

            if (avg > 0) {
                sumKnown += avg;
                knownCount++;
            }
        }

        double fallbackResponseTime = knownCount > 0 ? (sumKnown / knownCount) : 100.0;

        for (int i = 0; i < instances.size(); i++) {
            double responseTime = rawResponseTimes[i] > 0 ? rawResponseTimes[i] : fallbackResponseTime;
            weights[i] = 1.0 / responseTime;
        }
        return weights;
    }

    private ServiceInstance pickByWeight(List<ServiceInstance> instances, double[] weights) {
        double totalWeight = 0;
        for (double w : weights) {
            totalWeight += w;
        }

        double point = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (int i = 0; i < instances.size(); i ++) {
            cumulative += weights[i];
            if (point <= cumulative) {
                return instances.get(i);
            }
        }
        return instances.getLast();
    }

    private String instancesKey(ServiceInstance instance) {
        return instance.getInstanceId() != null
                ? instance.getInstanceId()
                : instance.getHost() + ":" + instance.getPort();
    }
}
