package com.bgsrc.spring.cloud.user.controller;

import com.bgsrc.spring.cloud.user.api.UserServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController implements UserServer {

    @GetMapping("{uid}")
    public String getUserInfo(@PathVariable String uid) {
        return String.format("this is %s user info.", uid);
    }

}
