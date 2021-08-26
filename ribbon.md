## Ribbon源码分析
### 猜想
Ribbon 基本使用步骤：

1. 引入spring-cloud-starter-netflix-ribbon依赖。
2. 在RestTemplate的定义上添加`@LoadBalanced`。
3. application.properties 配置<client>.ribbon.listOfServers。

结合ribbon基本使用步骤做如下猜想：
1. Ribbon的加载一定是基于Spring Boot自动装配机制实现的；
2. 添加了`@LoadBalanced`修饰的RestTemplate一定是做了相应的处理；
3. Ribbon一定是通过某种方法读取到了配置文件内容，创建了相应的实例，并根据<client>区分不同的实例。

### 分析验证
#### Spring容器加载
根据Spring Bean加载的过程探索Ribbon 源码实现。

Ribbon Bean的加载是基于Spring自动装配机制实现的，参见`spring-cloud-netflix-ribbon-2.2.3.RELEASE.jar!META-INF/spring.factories`。
* spring.factories文件内容
~~~properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration
~~~
> `RibbonAutoConfiguration` 配置类作为自动装配的起始入口。
* RibbonAutoConfiguration 实现Ribbon 核心类的加载。初始化`SpringClientFactory`、`RibbonLoadBalancerClient`、`PropertiesFactory`
~~~java
@Configuration
@Conditional(RibbonAutoConfiguration.RibbonClassesConditions.class)
@RibbonClients
@AutoConfigureAfter(
		name = "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration")
@AutoConfigureBefore({ LoadBalancerAutoConfiguration.class,
		AsyncLoadBalancerAutoConfiguration.class })
@EnableConfigurationProperties({ RibbonEagerLoadProperties.class,
		ServerIntrospectorProperties.class })
public class RibbonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringClientFactory springClientFactory() {
        SpringClientFactory factory = new SpringClientFactory();
        factory.setConfigurations(this.configurations);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(LoadBalancerClient.class)
    public LoadBalancerClient loadBalancerClient() {
        return new RibbonLoadBalancerClient(springClientFactory());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public PropertiesFactory propertiesFactory() {
        return new PropertiesFactory();
    }
}
~~~
> 需要关注的注解和配置类
> * `RibbonClients`
> * `LoadBalancerAutoConfiguration`
> * `AsyncLoadBalancerAutoConfiguration`

* **LoadBalancerAutoConfiguration**
是Spring Cloud提供的LoadBalancer配置类。
实现了`SmartInitializingSingleton`、`LoadBalancerInterceptor`、`RestTemplateCustomizer`、`LoadBalancerRequestFactory`初始化。

* AsyncLoadBalancerAutoConfiguration
使用AsyncRestTemplate情况下，初始化Ribbon对应的 Interceptor、Customizer。

* RibbonClients注解类
~~~java
@Configuration(proxyBeanMethods = false)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Documented
// 引入 RibbonClientConfigurationRegistrar类
@Import(RibbonClientConfigurationRegistrar.class)
public @interface RibbonClients {

	RibbonClient[] value() default {};

	Class<?>[] defaultConfiguration() default {};

}
~~~

* RibbonClientConfigurationRegistrar
针对`RibbonClients`、`RibbonClient`注解定义的Ribbon client进行解析，在容器中注入RibbonClientSpecification，beanName=<name>.RibbonClientSpecification。

#### 小结
猜想一已经得到验证，从配置类中梳理出以下需要关注的类或接口。但是猜想二还没有验证，因此还需要关注`@LoadBalanced`的使用。
按照jar 进行划分。内容如下：
> spring-cloud-commons包：
> 1. LoadBalancerClient 客户端负载均衡接口。猜测Ribbon一定是实现了LoadBalancerClient
> 2. LoadBalancerRequestFactory
> 3. RestTemplateCustomizer
> 4. LoadalancerInterceptor
> 5. LoadBalanced
> 
> spring-cloud-netflix-ribbon包：
> 1. RibbonLoadBalancerClient 实现了LoadBalancerClient接口的实现类。
> 2. SpringClientFactory
> 3. RibbonClientConfiguration
> 4. PropertiesFactory
>



### Spring Cloud 负载均衡(spring-cloud-commons)

* LoadBalanced
继承了`@Qualifier`注解。
在`LoadBalancerAutoConfiguration`中针对添加了`LoadBalanced`注解修饰的`RestTemplate`实例，自动添加`LoadBalancerInterceptor`拦截器。
~~~java
public class LoadBalancerAutoConfiguration {
    @LoadBalanced
    @Autowired(required = false)
    private List<RestTemplate> restTemplates = Collections.emptyList();
    
	@Bean
	public SmartInitializingSingleton loadBalancedRestTemplateInitializerDeprecated(
			final ObjectProvider<List<RestTemplateCustomizer>> restTemplateCustomizers) {
		return () -> restTemplateCustomizers.ifAvailable(customizers -> {
			for (RestTemplate restTemplate : LoadBalancerAutoConfiguration.this.restTemplates) {
				for (RestTemplateCustomizer customizer : customizers) { 
				    // 添加LoadBalancerInterceptor拦截器
					customizer.customize(restTemplate);
				}
			}
		});
	}
}
~~~

* RestTemplateCustomizer 接口
~~~java
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("org.springframework.retry.support.RetryTemplate")
static class LoadBalancerInterceptorConfig {
    @Bean
    @ConditionalOnMissingBean
    public RestTemplateCustomizer restTemplateCustomizer(
          final LoadBalancerInterceptor loadBalancerInterceptor) {
      // 匿名内部类，对RestTemplate 增加LoadBalancerInterceptor拦截器。
      return restTemplate -> {
          List<ClientHttpRequestInterceptor> list = new ArrayList<>(
                  restTemplate.getInterceptors());
          list.add(loadBalancerInterceptor);
          restTemplate.setInterceptors(list);
      };
    }
}
~~~

* LoadalancerInterceptor
实现ClientHttpRequestInterceptor接口，对RestTemplate 进行拦截。
~~~java
public class LoadBalancerInterceptor implements ClientHttpRequestInterceptor {
	@Override
	public ClientHttpResponse intercept(final HttpRequest request, final byte[] body,
			final ClientHttpRequestExecution execution) throws IOException {
		final URI originalUri = request.getURI();
        // host 即为serviceName
		String serviceName = originalUri.getHost();
		Assert.state(serviceName != null,
				"Request URI does not contain a valid hostname: " + originalUri);
		return this.loadBalancer.execute(serviceName,
				this.requestFactory.createRequest(request, body, execution));
	}
}
~~~

* LoadBalancerRequestFactory
工厂类，创建LoadBalancerRequest实例。
~~~java
public class LoadBalancerRequestFactory {
    public LoadBalancerRequest<ClientHttpResponse> createRequest(
            final HttpRequest request, final byte[] body,
            final ClientHttpRequestExecution execution) {
        // LoadBalancerRequest匿名内部类
        return instance -> {
            HttpRequest serviceRequest = new ServiceRequestWrapper(request, instance,
                    this.loadBalancer);
            if (this.transformers != null) {
                for (LoadBalancerRequestTransformer transformer : this.transformers) {
                    serviceRequest = transformer.transformRequest(serviceRequest,
                            instance);
                }
            }
            return execution.execute(serviceRequest, body);
        };
    }
}
~~~

* ServiceRequestWrapper
集成HttpRequestWrapper的包装类，实现URI的替换。
~~~java
public class ServiceRequestWrapper extends HttpRequestWrapper {
    @Override
    public URI getURI() {
        // url被替换
        URI uri = this.loadBalancer.reconstructURI(this.instance, getRequest().getURI());
        return uri;
    }
}
~~~

### Ribbon Spring Cloud相关类(spring-cloud-netflix-ribbon)


* `RibbonLoadBalancerClient`实现`org.springframework.cloud.client.loadbalancer.LoadBalancerClient`接口，Rebbion客户端负载均衡整合Spring Cloud的核心类。
    * reconstructURI()，调用：ServiceRequestWrapper.getURI()
~~~java
public class RibbonLoadBalancerClient implements LoadBalancerClient { 
	@Override
	public URI reconstructURI(ServiceInstance instance, URI original) {
		Assert.notNull(instance, "instance can not be null");
		String serviceId = instance.getServiceId();
		RibbonLoadBalancerContext context = this.clientFactory
				.getLoadBalancerContext(serviceId);

		URI uri;
		Server server;
		if (instance instanceof RibbonServer) {
			RibbonServer ribbonServer = (RibbonServer) instance;
			server = ribbonServer.getServer();
			uri = updateToSecureConnectionIfNeeded(original, ribbonServer);
		}
		else {
			server = new Server(instance.getScheme(), instance.getHost(),
					instance.getPort());
			IClientConfig clientConfig = clientFactory.getClientConfig(serviceId);
			ServerIntrospector serverIntrospector = serverIntrospector(serviceId);
			uri = updateToSecureConnectionIfNeeded(original, clientConfig,
					serverIntrospector, server);
		}
		return context.reconstructURIWithServer(server, uri);
	}

    @Override
    public <T> T execute(String serviceId, LoadBalancerRequest<T> request)
            throws IOException {
        return execute(serviceId, request, null);
    }
    
	public <T> T execute(String serviceId, LoadBalancerRequest<T> request, Object hint)
			throws IOException {
        // 根据serviceId 获取 LoadBalancer
		ILoadBalancer loadBalancer = getLoadBalancer(serviceId);
        // 获取具体的服务实例 loadBalancer.chooseServer(hint != null ? hint : "default");
		Server server = getServer(loadBalancer, hint);
		if (server == null) {
			throw new IllegalStateException("No instances available for " + serviceId);
		}
		RibbonServer ribbonServer = new RibbonServer(serviceId, server,
				isSecure(server, serviceId),
				serverIntrospector(serviceId).getMetadata(server));

		return execute(serviceId, ribbonServer, request);
	}
    
    @Override
	public <T> T execute(String serviceId, ServiceInstance serviceInstance,
			LoadBalancerRequest<T> request) throws IOException {
		Server server = null;
		if (serviceInstance instanceof RibbonServer) {
			server = ((RibbonServer) serviceInstance).getServer();
		}
		if (server == null) {
			throw new IllegalStateException("No instances available for " + serviceId);
		}

		RibbonLoadBalancerContext context = this.clientFactory
				.getLoadBalancerContext(serviceId);
		RibbonStatsRecorder statsRecorder = new RibbonStatsRecorder(context, server);

		try {
			T returnVal = request.apply(serviceInstance);
			statsRecorder.recordStats(returnVal);
			return returnVal;
		}
		// catch IOException and rethrow so RestTemplate behaves correctly
		catch (IOException ex) {
			statsRecorder.recordStats(ex);
			throw ex;
		}
		catch (Exception ex) {
			statsRecorder.recordStats(ex);
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		return null;
	}
}
~~~
* SpringClientFactory
~~~java
public class SpringClientFactory extends NamedContextFactory<RibbonClientSpecification> {
    static final String NAMESPACE = "ribbon";

    public SpringClientFactory() {
        super(RibbonClientConfiguration.class, NAMESPACE, "ribbon.client.name");
    }
}
~~~
> 继承NamedContextFactory抽象类，实现根据name值（serviceId）创建对应的ApplicationContext子容器。
* RibbonClientConfiguration
~~~java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
// Order is important here, last should be the default, first should be optional
// see
// https://github.com/spring-cloud/spring-cloud-netflix/issues/2086#issuecomment-316281653
@Import({ HttpClientConfiguration.class, OkHttpRibbonConfiguration.class,
		RestClientRibbonConfiguration.class, HttpClientRibbonConfiguration.class })
public class RibbonClientConfiguration {
    @RibbonClientName
    private String name = "client";
    
    /* 实现IClientConfig、IRule、IPing、ILoadBalancer、RibbonLoadBalancerContext等核心类的初始化 */
}
~~~
* PropertiesFactory
实现Ribbon Client 自定义配置参数创建，对应Spring Cloud Netflix 文档中7.4章节。


### Ribbon 核心类
* IClientConfig
    * DefaultClientConfigImpl
    
* ILoadBalancer
    * ZoneAwareLoadBalancer（默认) 
> Ping: 设置Ping时同步启动PingTask（Timer，默认10S执行一次），Pinger 调用IPingStrategy + IPing 实现。
> Rule: 路由规则
> ServerList: 服务列表
> ServerListUpdater: 服务列表更新         

* IRule
    * ZoneAvoidanceRule（默认）
    * RandomRule
    * ResponseTimeWeightedRule

* IPing
    * DummyPing（默认）
    * NIWSDiscoveryPing
> 
* ServerList
    * ConfigurationBasedServerList（默认）
    * DomainExtractingServerList（Eureka）
> 服务列表获取
* ServerListUpdater
    * PollingServerListUpdater（默认)
> 定时更新服务列表（ScheduledThreadPoolExecutor）
* ServerListFilter
    * ZonePreferenceServerListFilter
    
* RibbonLoadBalancerContext
    * PollingServerListUpdater
    
* RetryHandler

* ServerIntrospector
    * DefaultServerIntrospector
    
* CommonClientConfigKey
    