package com.nhnacademy.insightongateway.loadbalancer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResponseTimeRegistry {

    private static final double ALPHA = 0.2; // 가중치 상수

    // key: insighton-ai:insighton-ai-1, value: 평균 응답시간(ms)
    private final Map<String, Double> avgResponseTimes = new ConcurrentHashMap<>();

    /**
     * @param instanceKey 어느 인스턴스인지
     * @param elapsedMillis 이번에 몇 ms 걸렸는지
     */
    public void recordResponseTime(String instanceKey, long elapsedMillis) {
        avgResponseTimes.merge(instanceKey, (double) elapsedMillis,
                (oldAvg, newVal) -> ALPHA * newVal + (1 - ALPHA) * oldAvg);
        // 새 평균 = 0.2 × 이번 응답시간 + 0.8 × 기존 평균 (EMA)
    }

    public double getAverage(String instanceKey) {
        return avgResponseTimes.getOrDefault(instanceKey, -1.0); // -1.0 은 이 인스턴스는 아직 데이터가 없음을 뜻함
    }

    public Map<String ,Double> snapshot() {
        return Map.copyOf(avgResponseTimes);
    }
}
