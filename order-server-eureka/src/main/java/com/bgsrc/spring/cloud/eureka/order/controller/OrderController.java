package com.bgsrc.spring.cloud.eureka.order.controller;

import com.bgsrc.spring.cloud.eureka.order.reference.UserRemoteServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/order")
@RestController
public class OrderController {

    @Autowired
    private UserRemoteServer userRemoteServer;

    @GetMapping("/{oid}")
    public String orderInfo(@PathVariable("oid") String oid) {
        String userInfo = userRemoteServer.getUserInfo("5876");
        log.info("user info: {}", userInfo);
        return oid + " order info ";
    }
}
