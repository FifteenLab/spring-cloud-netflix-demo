package com.bgsrc.spring.cloud.eureka.order.reference;


import com.bgsrc.spring.cloud.user.api.UserServer;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(UserServer.serverName)
public interface UserRemoteServer extends UserServer {
}
