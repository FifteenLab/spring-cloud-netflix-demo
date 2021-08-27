* [Ribbon](#ribbon)
  * [官网文档](#官网文档)
  * [基本使用](#基本使用)
  * [源码分析](#源码分析)
* [Feign](#feign)
  * [基本使用](#基本使用-1)
  * [Client 扩展](#client-扩展)
     * [使用OkHttp](#使用okhttp)
     * [使用HttpClient](#使用httpclient)
     * [使用HttpClient5](#使用httpclient5)
* [Eureka Server](#eureka-server)
  * [官网文档](#官网文档-1)
  * [基本使用](#基本使用-2)
  * [Eureka Server 安全](#eureka-server-安全)
* [Eureka Client](#eureka-client)
  * [官网文档](#官网文档-2)
  * [基本使用](#基本使用-3)
* [Actuator](#actuator)
  * [官网文档](#官网文档-3)
  * [基本使用](#基本使用-4)

## Ribbon
### 官网文档
[Spring Cloud Netflix Ribbon](https://docs.spring.io/spring-cloud-netflix/docs/2.2.9.RELEASE/reference/html/#spring-cloud-ribbon)

### 基本使用
1、添加Ribbon Starter依赖
~~~xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-ribbon</artifactId>
    <version>${spring-cloud.version}</version>
</dependency>
~~~

2、配置RestTemplate，添加LoadBalanced 注解
~~~java
@Configuration
public class RestConfiguration {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }
}
~~~

3、配置文件中定义服务列表
~~~properties
user-server.ribbon.listOfServers=localhost:8081,localhost:8083
~~~

### 源码分析
参见 [Ribbon.md](ribbon.md)

## Feign 
### 

### 基本使用
1、添加openFeign 依赖包
~~~xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
    <version>${spring-cloud.version}</version>
</dependency>
~~~
2、创建FeignClient接口类，添加FeignClient 注解
~~~java
@FeignClient("user-server")
@RequestMapping("/user")
public interface UserService {

    @GetMapping("/{uid}")
    String getUserInfo(@PathVariable("uid") String uid);
}
~~~
3、启动类增加EnableFeignClients 注解
~~~java
@EnableFeignClients
@SpringBootApplication
public class OrderServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServerApplication.class, args);
    }
}
~~~

### Client 扩展
#### 使用OkHttp
1、添加OkHttp依赖包。
~~~xml
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-okhttp</artifactId>
    <version>10.12</version>
</dependency>
~~~
2、修改application.properties配置文件，开启OkHttp Client。
~~~properties
feign.okhttp.enabled=true
~~~
#### 使用HttpClient

#### 使用HttpClient5
 
## Eureka Server
### 官网文档
[Spring Cloud Netflix Eureka Server](https://docs.spring.io/spring-cloud-netflix/docs/2.2.9.RELEASE/reference/html/#spring-cloud-eureka-server)

### 基本使用
参考：https://docs.spring.io/spring-cloud-netflix/docs/2.2.9.RELEASE/reference/html/#netflix-eureka-server-starter

1、创建Spring-boot项目 pom.xml 添加eureka-server依赖包
~~~xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
~~~
2、启动类添加EnableEurekaServer注解
~~~java
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaServerApplication.class, args);
	}

}
~~~
3、application.properties 配置文件添加eureka-server 配置，包括端口配置
~~~properties
# 服务启动端口号
server.port=8761
# 定义服务名称
spring.application.name=example-eureka-server

# 是否注册到eureka中
eureka.client.registerWithEureka=false
# 是否获取服务信息
eureka.client.fetchRegistry=false
# 注册中心服务地址
eureka.client.serviceUrl.defaultZone=http://${eureka.instance.hostname}:${server.port}/eureka/
~~~

4、启动spring-boot项目，浏览器访问http://localhost:8761

### Eureka Server 安全

参考：[securing-the-eureka-server](https://docs.spring.io/spring-cloud-netflix/docs/2.2.9.RELEASE/reference/html/#securing-the-eureka-server)

1、引入Spring Security 依赖
~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
~~~
2、针对Eureka 客户端屏蔽CSRF
~~~java
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().ignoringAntMatchers("/eureka/**");
        super.configure(http);
    }
}
~~~
3、配置文件中配置登录用户名密码
~~~properties
spring.security.user.name=admin
spring.security.user.password=123456
~~~

## Eureka Client
### 官网文档
[Spring Cloud Netflix Eureka Client](https://docs.spring.io/spring-cloud-netflix/docs/2.2.9.RELEASE/reference/html/#service-discovery-eureka-clients)


### 基本使用
1、Spring Boot项目添加eureka-client 依赖。
~~~xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
~~~
2、配置文件配置Eureka 注册中心地址。
~~~properties
eureka.client.serviceUrl.defaultZone=http://admin:123456@127.0.0.1:8761/eureka/
~~~
3、启动类增加EnableEurekaClient注解。
~~~java
@EnableEurekaClient
@SpringBootApplication
public class UserServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServerApplication.class, args);
    }

}
~~~

## Actuator
### 官网文档
[Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.enablin)

### 基本使用
1、添加Actuator依赖。
~~~xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
~~~
2、配置文件中开启需要的endpoint。
~~~properties
# jmx
management.endpoints.jmx.exposure.include=*
# http协议
management.endpoints.web.exposure.include=*
~~~
