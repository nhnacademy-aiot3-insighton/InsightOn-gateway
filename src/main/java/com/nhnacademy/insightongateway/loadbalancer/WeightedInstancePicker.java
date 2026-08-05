package com.nhnacademy.insightongateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WeightedInstancePicker implements InstancePickStrategy {

    private final ResponseTimeRegistry registry;

    public WeightedInstancePicker(ResponseTimeRegistry registry) {
        this.registry = registry;
    }

    @Override
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

    // 가중치 계산
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

    // TODO: 최대 가중치 상한(cap) 설정, 서킷 브레이커와 조합, active request count 기반 가중치 추가 고려
    // 가중치 기반 랜던 선택 - 룰렛 휠 알고리즘
    private ServiceInstance pickByWeight(List<ServiceInstance> instances, double[] weights) {
        // STEP 1: 전체 가중치 합 구하기
        double totalWeight = 0;
        for (double w : weights) {
            totalWeight += w;
        }

        // STEP 2: Random 점 찍기
        // Random 일 경우 여러 스레드가 동시에 nextDouble()을 호출하면, CAS(compare-and-swap) 연간에서 contention이 생김
        // 요청이 많아질수록 이 경합이 병목이 될 가능성이 있음
        // ThreadLocalRandom: 각 thread가 자기 자신만의 랜던 생성기를 사용하기 때문에 contention 자체가 없음
        double point = ThreadLocalRandom.current().nextDouble() * totalWeight;

        // STEP 3: 누적하면서 구간 찾기
        double cumulative = 0;

        for (int i = 0; i < instances.size(); i ++) {
            cumulative += weights[i];

            // 각 인스턴스의 가중치 구간을 순서대로 이어붙이면서, 랜점 점이 그 구간 안에 들어오는 순간 멈춤
            if (point <= cumulative) {
                return instances.get(i);
            }
        }
        // STEP 4: 안전장치
        return instances.getLast();
    }

    private String instancesKey(ServiceInstance instance) {
        return instance.getInstanceId() != null
                ? instance.getInstanceId()
                : instance.getHost() + ":" + instance.getPort();
    }
}
