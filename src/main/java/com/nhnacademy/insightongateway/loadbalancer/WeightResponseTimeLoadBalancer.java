package com.nhnacademy.insightongateway.loadbalancer;

import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@NullMarked // 이 안에 잇는 모든 타입은 별도 표시가 없으면 기본적으로 non-null
public class WeightResponseTimeLoadBalancer implements ReactorLoadBalancer<ServiceInstance> {

    // 지금 살아있는 인스턴스 목록을 나중에 필요할 때 꺼내쓸 수 있는 provider
    // ObjectProvider로 감싼 이유: Bean 생성 시점에 즉시 꺼내지 않고 실제로 로드밸런싱 결정을 내리는 순간까지 지연시켜서, Spring Cloud LoadBalancer가 서비스별로 만드는 자식 컨텍스트의 초기화 순서 문제를 피하기 위함
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    // 실제로 누굴 고를지 판단하는 로직을 담은 객체
    private final WeightedInstancePicker picker;

    public WeightResponseTimeLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
                                          WeightedInstancePicker picker) {
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.picker = picker;
    }


    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request)
                .next()
                .map(this::pickInstance);
    }

    private Response<ServiceInstance> pickInstance(List<ServiceInstance> instances) {
        ServiceInstance chosen = picker.pick(instances);
        if (Objects.isNull(chosen)) {
            return new EmptyResponse();
        }
        return new DefaultResponse(chosen);
    }
}
