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

    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;
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
