package com.nhnacademy.insightongateway.loadbalancer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResponseTimeRegistry {

    private static final double ALPHA = 0.2;

    private final Map<String, Double> avgResponseTimes = new ConcurrentHashMap<>();

    // 새 평균 = 0.2 × 이번 응답시간 + 0.8 × 기존 평균 (EMA)
    public void record(String instanceKey, long elapsedMillis) {
        avgResponseTimes.merge(instanceKey, (double) elapsedMillis,
                (oldAvg, newVal) -> ALPHA * newVal + (1 - ALPHA) * oldAvg);
    }

    public double getAverage(String instanceKey) {
        return avgResponseTimes.getOrDefault(instanceKey, -1.0);
    }

    public Map<String ,Double> snapshot() {
        return Map.copyOf(avgResponseTimes);
    }
}
