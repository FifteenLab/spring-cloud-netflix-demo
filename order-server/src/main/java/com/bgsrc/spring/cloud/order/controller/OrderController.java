package com.bgsrc.spring.cloud.order.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RequestMapping("/order")
@RestController
@Slf4j
public class OrderController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LoadBalancerClient loadBalancerClient;

    @GetMapping("{oid}")
    public String getOrder(@PathVariable String oid) {
        ResponseEntity<String> responseEntity = restTemplate.getForEntity("http://user-server/user/56432", String.class);
        log.info("user-server <<< {} {}", responseEntity.getStatusCode(), responseEntity.getStatusCodeValue());
        log.info("user-server <<< {}", responseEntity.getBody());
        return oid + " order info.";
    }

    @GetMapping("/ribbon/{oid}")
    public String getOrderByRibbonApi(@PathVariable String oid) {
        ServiceInstance instance = loadBalancerClient.choose("user-server");

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(String.format("http://%s:%d/user/56432", instance.getHost(), instance.getPort()), String.class);
        log.info("user-server <<< {} {}", responseEntity.getStatusCode(), responseEntity.getStatusCodeValue());
        log.info("user-server <<< {}", responseEntity.getBody());
        return oid + " order info.";
    }
}
