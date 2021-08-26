package com.bgsrc.spring.cloud.order.controller;

import com.bgsrc.spring.cloud.order.remote.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/feign/order")
public class OrderFeignController {

    @Autowired
    private UserService userService;

    @GetMapping("{oid}")
    public String getOrder(@PathVariable("oid") String oid) {
        String user = userService.getUserInfo("5647");
        log.info("user-server << {}", user);
        return oid + " order info";
    }

}
