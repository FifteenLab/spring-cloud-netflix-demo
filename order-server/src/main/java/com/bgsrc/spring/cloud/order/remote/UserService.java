package com.bgsrc.spring.cloud.order.remote;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient("user-server")
@RequestMapping("/user")
public interface UserService {

    @GetMapping("/{uid}")
    String getUserInfo(@PathVariable("uid") String uid);
}
