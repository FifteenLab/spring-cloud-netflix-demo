* [猜想](#猜想)
* [分析验证](#分析验证)
  * [Spring 容器装载](#spring-容器装载)
     * [EnableFeignClients注解](#enablefeignclients注解)
     * [FeignClientsRegistrar](#feignclientsregistrar)
     * [FeignClientFactoryBean](#feignclientfactorybean)
     * [spring.factories文件](#springfactories文件)
     * [FeignAutoConfiguration](#feignautoconfiguration)
     * [FeignRibbonClientAutoConfiguration](#feignribbonclientautoconfiguration)
     * [FeignContext](#feigncontext)
     * [FeignClientsConfiguration](#feignclientsconfiguration)
     * [继续 FeignClientFactoryBean](#继续-feignclientfactorybean)
     * [DefaultTargeter](#defaulttargeter)
     * [小结](#小结)
  * [Feign核心接口/类](#feign核心接口类)
     * [Feign.Builder](#feignbuilder)
     * [ReflectiveFeign](#reflectivefeign)
     * [InvocationHandlerFactory.Default](#invocationhandlerfactorydefault)
     * [ReflectiveFeign.FeignInvocationHandler](#reflectivefeignfeigninvocationhandler)
     * [ReflectiveFeign.ParseHandlersByName](#reflectivefeignparsehandlersbyname)
     * [SpringMvcContract](#springmvccontract)
     * [SynchronousMethodHandler.Factory](#synchronousmethodhandlerfactory)
     * [SynchronousMethodHandler](#synchronousmethodhandler)
     * [小结](#小结-1)
* [总结](#总结)

## 猜想
Feign 基本使用步骤：
1. 引入`spring-cloud-starter-openfeign`依赖包。
2. 定义接口，接口添加`@FeignClient`注解修饰。`@FeignClient`中定义调用地址或者服务名。
3. 启动类添加`@EnableFeignClients`。

完成以上操作对于需要调用接口的地方直接注入即可。

结合Feign 的使用步骤作如下猜想：
1. 添加了`@FeignClient`修饰的接口，可以使用Spring注入，那么Spring 容器一定存在接口的代理类。
2. 启动类上添加`@EnableFeignClients`，应该是这个注解通过某种方式创建了被`@FeignClient`修饰的接口的代理类，并注入到Spring 容器中。
3. `FeignClient`中可以定义调用地址或服务名，如果定义服务名，那么URI替换有可能是Ribbon实现的。

## 分析验证

项目引入`spring-cloud-starter-openfeign`后，执行`mvn dependency:tree`查看依赖树。
~~~shell script
[INFO] +- org.springframework.cloud:spring-cloud-starter-openfeign:jar:2.2.8.RELEASE:compile
[INFO] |  +- org.springframework.cloud:spring-cloud-starter:jar:2.2.8.RELEASE:compile
[INFO] |  |  +- org.springframework.cloud:spring-cloud-context:jar:2.2.8.RELEASE:compile
[INFO] |  |  \- org.springframework.security:spring-security-rsa:jar:1.0.9.RELEASE:compile
[INFO] |  |     \- org.bouncycastle:bcpkix-jdk15on:jar:1.64:compile
[INFO] |  |        \- org.bouncycastle:bcprov-jdk15on:jar:1.64:compile
[INFO] |  +- org.springframework.cloud:spring-cloud-openfeign-core:jar:2.2.8.RELEASE:compile
[INFO] |  |  +- org.springframework.boot:spring-boot-starter-aop:jar:2.3.0.RELEASE:compile
[INFO] |  |  |  \- org.aspectj:aspectjweaver:jar:1.9.5:compile
[INFO] |  |  \- io.github.openfeign.form:feign-form-spring:jar:3.8.0:compile
[INFO] |  |     +- io.github.openfeign.form:feign-form:jar:3.8.0:compile
[INFO] |  |     \- commons-fileupload:commons-fileupload:jar:1.4:compile
[INFO] |  |        \- commons-io:commons-io:jar:2.2:compile
[INFO] |  +- org.springframework.cloud:spring-cloud-commons:jar:2.2.8.RELEASE:compile
[INFO] |  |  \- org.springframework.security:spring-security-crypto:jar:5.3.2.RELEASE:compile
[INFO] |  +- io.github.openfeign:feign-core:jar:10.12:compile
[INFO] |  +- io.github.openfeign:feign-slf4j:jar:10.12:compile
[INFO] |  |  \- org.slf4j:slf4j-api:jar:1.7.30:compile
[INFO] |  \- io.github.openfeign:feign-hystrix:jar:10.12:compile
[INFO] |     +- com.netflix.archaius:archaius-core:jar:0.7.6:compile
[INFO] |     |  \- com.google.code.findbugs:jsr305:jar:3.0.1:runtime
[INFO] |     \- com.netflix.hystrix:hystrix-core:jar:1.5.18:compile
[INFO] |        \- org.hdrhistogram:HdrHistogram:jar:2.1.9:compile
~~~
通过maven依赖树可以看到引入`spring-cloud-starter-openfeign`后，引入了`spring-cloud-openfeign-core`和`feign-core`以及`feign-hystrix`。
### Spring 容器装载 

#### EnableFeignClients注解
首先，关注`@EnableFeignClients`源码实现。
~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
// Bingo Import 注解
@Import(FeignClientsRegistrar.class)
public @interface EnableFeignClients {
    /* 省略 */
}
~~~
#### FeignClientsRegistrar
`FeignClientsRegistrar`实现了`ImportBeanDefinitionRegistrar`接口。下面重点关注`registerBeanDefinitions`方法实现。
~~~java
class FeignClientsRegistrar
		implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {
        registerDefaultConfiguration(metadata, registry);
        registerFeignClients(metadata, registry);
    }
    
    private void registerDefaultConfiguration(AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {
        Map<String, Object> defaultAttrs = metadata
                .getAnnotationAttributes(EnableFeignClients.class.getName(), true);
    
        if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
            String name;
            if (metadata.hasEnclosingClass()) {
                name = "default." + metadata.getEnclosingClassName();
            }
            else {
                name = "default." + metadata.getClassName();
            }
            registerClientConfiguration(registry, name,
                    defaultAttrs.get("defaultConfiguration"));
        }
    }

    public void registerFeignClients(AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {
    
        LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
        Map<String, Object> attrs = metadata
                .getAnnotationAttributes(EnableFeignClients.class.getName());
        final Class<?>[] clients = attrs == null ? null
                : (Class<?>[]) attrs.get("clients");
        if (clients == null || clients.length == 0) {
            // EnableFeignClients 未定义Feign Client的情况下
            ClassPathScanningCandidateComponentProvider scanner = getScanner();
            scanner.setResourceLoader(this.resourceLoader);
            // Bingo 根据注解`FeignClient`扫描
            scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));
            Set<String> basePackages = getBasePackages(metadata);
            for (String basePackage : basePackages) {
                candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
            }
        }
        else {
            for (Class<?> clazz : clients) {
                candidateComponents.add(new AnnotatedGenericBeanDefinition(clazz));
            }
        }
    
        for (BeanDefinition candidateComponent : candidateComponents) {
            if (candidateComponent instanceof AnnotatedBeanDefinition) {
                // verify annotated class is an interface
                AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
                Assert.isTrue(annotationMetadata.isInterface(),
                        "@FeignClient can only be specified on an interface");
    
                Map<String, Object> attributes = annotationMetadata
                        .getAnnotationAttributes(FeignClient.class.getCanonicalName());
    
                String name = getClientName(attributes);
                // 每个FeignClient 上定义的配置
                registerClientConfiguration(registry, name,
                        attributes.get("configuration"));
                // Bingo 将扫描的到Class注册到容器中
                registerFeignClient(registry, annotationMetadata, attributes);
            }
        }
    }
}
~~~
下面重点关注`FeignClientsRegistrar.registerFeignClient`方法，了解Spring容器中注册的到底是什么，不出意外的话应该是FactoryBean。
~~~java
class FeignClientsRegistrar
		implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    private void registerFeignClient(BeanDefinitionRegistry registry,
			AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
        String className = annotationMetadata.getClassName();
        Class clazz = ClassUtils.resolveClassName(className, null);
        ConfigurableBeanFactory beanFactory = registry instanceof ConfigurableBeanFactory
                ? (ConfigurableBeanFactory) registry : null;
        String contextId = getContextId(beanFactory, attributes);
        String name = getName(attributes);
        // Bingo 快看这里创建了一个FactoryBean
        FeignClientFactoryBean factoryBean = new FeignClientFactoryBean();
        factoryBean.setBeanFactory(beanFactory);
        factoryBean.setName(name);
        factoryBean.setContextId(contextId);
        factoryBean.setType(clazz);
        BeanDefinitionBuilder definition = BeanDefinitionBuilder
                .genericBeanDefinition(clazz, () -> {
                    // Supplier.get() 哎~，猜错了，用的是JDK 1.8 提供的Supplier，实际还是由FeignClientFactoryBean创建的。
                    factoryBean.setUrl(getUrl(beanFactory, attributes));
                    factoryBean.setPath(getPath(beanFactory, attributes));
                    factoryBean.setDecode404(Boolean
                            .parseBoolean(String.valueOf(attributes.get("decode404"))));
                    Object fallback = attributes.get("fallback");
                    if (fallback != null) {
                        factoryBean.setFallback(fallback instanceof Class
                                ? (Class<?>) fallback
                                : ClassUtils.resolveClassName(fallback.toString(), null));
                    }
                    Object fallbackFactory = attributes.get("fallbackFactory");
                    if (fallbackFactory != null) {
                        factoryBean.setFallbackFactory(fallbackFactory instanceof Class
                                ? (Class<?>) fallbackFactory
                                : ClassUtils.resolveClassName(fallbackFactory.toString(),
                                        null));
                    }
                    return factoryBean.getObject();
                });
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        definition.setLazyInit(true);
        validate(attributes);
    
        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, className);
        beanDefinition.setAttribute("feignClientsRegistrarFactoryBean", factoryBean);
    
        // has a default, won't be null
        boolean primary = (Boolean) attributes.get("primary");
    
        beanDefinition.setPrimary(primary);
    
        String[] qualifiers = getQualifiers(attributes);
        if (ObjectUtils.isEmpty(qualifiers)) {
            qualifiers = new String[] { contextId + "FeignClient" };
        }
    
        BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
                qualifiers);
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
    }
}
~~~
#### FeignClientFactoryBean
接下来聚焦到`FeignClientFactoryBean`，看一看`FeignClientFactoryBean`具体是怎么创建Bean的。
~~~java
public class FeignClientFactoryBean implements FactoryBean<Object>, InitializingBean,
		ApplicationContextAware, BeanFactoryAware {
    @Override
    public Object getObject() {
        return getTarget();
    }
    
    <T> T getTarget() {
        // 停~~ ，好像忽略了些什么，哪儿来的 FeignContext？
        FeignContext context = beanFactory != null
                ? beanFactory.getBean(FeignContext.class)
                : applicationContext.getBean(FeignContext.class);
        /* 忽略 */
    }
}
~~~
#### spring.factories文件
找到`spring-cloud-openfeign-core-2.2.8.RELEASE.jar!/META-INF/spring.factories`，来看看`spring.factories`定义了什么。
~~~properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.cloud.openfeign.ribbon.FeignRibbonClientAutoConfiguration,\
org.springframework.cloud.openfeign.hateoas.FeignHalAutoConfiguration,\
org.springframework.cloud.openfeign.FeignAutoConfiguration,\
org.springframework.cloud.openfeign.encoding.FeignAcceptGzipEncodingAutoConfiguration,\
org.springframework.cloud.openfeign.encoding.FeignContentGzipEncodingAutoConfiguration,\
org.springframework.cloud.openfeign.loadbalancer.FeignLoadBalancerAutoConfiguration
~~~
> FeignAutoConfiguration 装载Feign核心组件，包括FeignContext、Targeter。
> FeignRibbonClientAutoConfiguration 项目中存在Ribbon负载均衡时实现基于Ribbon 的Client创建。支持HttpClient、OkHttp等。
> FeignAcceptGzipEncodingAutoConfiguration、FeignContentGzipEncodingAutoConfiguration 报文压缩
> FeignLoadBalancerAutoConfiguration 非Ribbon情况下创建Client。

#### FeignAutoConfiguration
~~~java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Feign.class)
@EnableConfigurationProperties({ FeignClientProperties.class,
		FeignHttpClientProperties.class, FeignEncoderProperties.class })
@Import(DefaultGzipDecoderConfiguration.class)
public class FeignAutoConfiguration {
    
    // 装载 FeignContext
    @Bean
    public FeignContext feignContext() {
        FeignContext context = new FeignContext();
        context.setConfigurations(this.configurations);
        return context;
    }
    
    
    @Configuration(proxyBeanMethods = false)
    @Conditional(DefaultFeignTargeterConditions.class)
    protected static class DefaultFeignTargeterConfiguration {
        
        // 装载 DefaultTargeter，
        @Bean
        @ConditionalOnMissingBean
        public Targeter feignTargeter() {
            return new DefaultTargeter();
        }

    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(FeignCircuitBreakerDisabledConditions.class)
    @ConditionalOnClass(name = "feign.hystrix.HystrixFeign")
    @ConditionalOnProperty(value = "feign.hystrix.enabled", havingValue = "true",
            matchIfMissing = true)
    protected static class HystrixFeignTargeterConfiguration {
        
        // 装载 HystrixTargeter，从字面上看应该是使用Hystrix的情况下装载的，这部分后续Hystrix的时候再分析
        @Bean
        @ConditionalOnMissingBean
        public Targeter feignTargeter() {
            return new HystrixTargeter();
        }
    
    }
}
~~~
从上面的源码里面可以找到`FeignContext`、`Targeter`的装载过程。现在客户端负载均衡用的Ribbon，因此下面先关注`FeignRibbonClientAutoConfiguration`。

#### FeignRibbonClientAutoConfiguration
实现`Client`的装载
~~~java
@ConditionalOnClass({ ILoadBalancer.class, Feign.class })
@ConditionalOnProperty(value = "spring.cloud.loadbalancer.ribbon.enabled",
		matchIfMissing = true)
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(FeignAutoConfiguration.class)
@EnableConfigurationProperties({ FeignHttpClientProperties.class })
// Order is important here, last should be the default, first should be optional
// see
// https://github.com/spring-cloud/spring-cloud-netflix/issues/2086#issuecomment-316281653
@Import({ HttpClientFeignLoadBalancedConfiguration.class,
		OkHttpFeignLoadBalancedConfiguration.class,
		HttpClient5FeignLoadBalancedConfiguration.class,
		DefaultFeignLoadBalancedConfiguration.class })
public class FeignRibbonClientAutoConfiguration {
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("org.springframework.retry.support.RetryTemplate")
    public CachingSpringLoadBalancerFactory cachingLBClientFactory(
            SpringClientFactory factory) {
        // 装载 CachingSpringLoadBalancerFactory
        // Bingo SpringClientFactory：这个工厂类在Ribbon中看到过，会根据serviceId 创建ApplicationContext子容器，构建Ribbon 需要的组件。
        return new CachingSpringLoadBalancerFactory(factory);
    }
    
    /* 未用到RetryTemplate，在RetryTemplate情况下创建CachingSpringLoadBalancerFactory就先忽略了。 */

    @Bean
    @ConditionalOnMissingBean
    public Request.Options feignRequestOptions() {
        return LoadBalancerFeignClient.DEFAULT_OPTIONS;
    }
}
~~~
> FeignRibbonClientAutoConfiguration 引入XXXFeignLoadBalancedConfiguration 配置类。
> XXXFeignLoadBalancedConfiguration 根据配置完成对应Client的装载。
> * HttpClientFeignLoadBalancedConfiguration 基于HttpClient的实现
> * OkHttpFeignLoadBalancedConfiguration 基于OkHttp的实现
> * HttpClient5FeignLoadBalancedConfiguration 基于HttpClient5的实现
> * DefaultFeignLoadBalancedConfiguration 默认实现

#### FeignContext
在继续`FeignClientFactoryBean`前，先来简单了解一下`FeignContext`是什么。
~~~java
// Bingo 又见到了熟悉的老朋友NamedContextFactory
public class FeignContext extends NamedContextFactory<FeignClientSpecification> {
    public FeignContext() {
        // 这里还有FeignClientsConfiguration，感觉这个配置类应该很关键
        super(FeignClientsConfiguration.class, "feign", "feign.client.name");
    }
    /* 省略 */
}
~~~
下面去看看`FeignClientsConfiguration`配置类的内容，验证一下猜想。
#### FeignClientsConfiguration
~~~java
@Configuration(proxyBeanMethods = false)
public class FeignClientsConfiguration {
    /* 定义了Decoder、Encoder、Contract、Logger、Feign.Builder、等，*/
    
    @Bean
    @ConditionalOnMissingBean
    public Contract feignContract(ConversionService feignConversionService) {
        boolean decodeSlash = feignClientProperties == null
                || feignClientProperties.isDecodeSlash();
        return new SpringMvcContract(this.parameterProcessors, feignConversionService,
                decodeSlash);
    }
    
	@Bean
	public FormattingConversionService feignConversionService() {
		FormattingConversionService conversionService = new DefaultFormattingConversionService();
		for (FeignFormatterRegistrar feignFormatterRegistrar : this.feignFormatterRegistrars) {
			feignFormatterRegistrar.registerFormatters(conversionService);
		}
		return conversionService;
	}

}
~~~

#### 继续 FeignClientFactoryBean
从之前按暂停键的地方继续往后读，看后面的实现吧。
~~~java
public class FeignClientFactoryBean implements FactoryBean<Object>, InitializingBean,
		ApplicationContextAware, BeanFactoryAware {
    <T> T getTarget() {
        FeignContext context = beanFactory != null
                ? beanFactory.getBean(FeignContext.class)
                : applicationContext.getBean(FeignContext.class);
        // 继续~~ 
        // 根据FeignContext构建 Feign.Builder
        Feign.Builder builder = feign(context);
    
        if (!StringUtils.hasText(url)) {
            // 未定义URL，表示需要通过负载均衡算法获取真实的URL
            if (url != null && LOG.isWarnEnabled()) {
                LOG.warn(
                        "The provided URL is empty. Will try picking an instance via load-balancing.");
            }
            else if (LOG.isDebugEnabled()) {
                LOG.debug("URL not provided. Will use LoadBalancer.");
            }
            // FeignClient注解定义的实例名称，没有http协议头时需要添加http协议头。
            if (!name.startsWith("http")) {
                url = "http://" + name;
            }
            else {
                url = name;
            }
            // 注解中定义了Path前缀
            url += cleanPath();
            return (T) loadBalance(builder, context, new HardCodedTarget<>(type, name, url));
        }
        // FeignClient注解中定义的URL地址
        if (StringUtils.hasText(url) && !url.startsWith("http")) {
            url = "http://" + url;
        }
        String url = this.url + cleanPath();
    
        Client client = getOptional(context, Client.class);
        if (client != null) {
            if (client instanceof LoadBalancerFeignClient) {
                // not load balancing because we have a url,
                // but ribbon is on the classpath, so unwrap
                client = ((LoadBalancerFeignClient) client).getDelegate();
            }
            if (client instanceof FeignBlockingLoadBalancerClient) {
                // not load balancing because we have a url,
                // but Spring Cloud LoadBalancer is on the classpath, so unwrap
                client = ((FeignBlockingLoadBalancerClient) client).getDelegate();
            }
            if (client instanceof RetryableFeignBlockingLoadBalancerClient) {
                // not load balancing because we have a url,
                // but Spring Cloud LoadBalancer is on the classpath, so unwrap
                client = ((RetryableFeignBlockingLoadBalancerClient) client)
                        .getDelegate();
            }
            builder.client(client);
        }
        Targeter targeter = get(context, Targeter.class);
        return (T) targeter.target(this, builder, context,
                new HardCodedTarget<>(type, name, url));
    }
    
    protected <T> T loadBalance(Feign.Builder builder, FeignContext context,
            HardCodedTarget<T> target) {
        // 从容器中获取Client
        Client client = getOptional(context, Client.class);
        if (client != null) {
            builder.client(client);
            // 从容器中获取Targeter
            Targeter targeter = get(context, Targeter.class);
            // 返回实例对象，此处返回的应该是代理类
            return targeter.target(this, builder, context, target);
        }
    
        throw new IllegalStateException(
                "No Feign Client for loadBalancing defined. Did you forget to include spring-cloud-starter-netflix-ribbon or spring-cloud-starter-loadbalancer?");
    }

}
~~~
在`FeignClientFactoryBean`中基本了解了，实例Bean的创建过程。根据接口上FeignClient注解定义，再结合`Client`、`Target`最终创建实例Bean。
实例Bean 的具体创建过程在`Targeter.target(...)`中实现。

在[`FeignAutoConfiguration`](#FeignAutoConfiguration)中了解到，现在Spring Context中的`Targeter`实例实际上是`DefaultTargeter`。因此下面继续去看看`DefaultTargeter`的实现。
#### DefaultTargeter
~~~java
class DefaultTargeter implements Targeter {

    @Override
    public <T> T target(FeignClientFactoryBean factory, Feign.Builder feign,
            FeignContext context, Target.HardCodedTarget<T> target) {
        return feign.target(target);
    }

}
~~~
打开`DefaultTargeter`的那一刻，多少还是有些失望的，本以为到此就结束了，这么看来这条线并未结束。还得看看`Feign.Builder.target(...)`实现。

#### 小结
> 在探索Spring Bean装载的过程中，不知不觉间已经把Feign Spirng Cloud相关的类以及实现分析完了，同时对Feign 的核心接口/类有了一个大概的认识。
> 将接口、类、配置类按照包进行分类如下：
> * spring-cloud-openfeign-core
> 1. EnableFeignClients
> 2. FeignClientsRegistrar
> 3. FeignClientFactoryBean
> 4. FeignAutoConfiguration
> 5. FeignRibbonClientAutoConfiguration
> 6. FeignContext
> 7. FeignClientsConfiguration
> 8. DefaultTargeter
> * feigon-core
> 1. Feign
> 2. ReflectiveFeign
> 3. Client
> 4. Targeter
> 5. Decoder
> 6. Encoder
> 7. Contract
> 8. Logger
### Feign核心接口/类

#### Feign.Builder
~~~java
public abstract class Feign {
    public static class Builder {
        private final List<RequestInterceptor> requestInterceptors =
            new ArrayList<RequestInterceptor>();
        private Logger.Level logLevel = Logger.Level.NONE;
        private Contract contract = new Contract.Default();
        private Client client = new Client.Default(null, null);
        private Retryer retryer = new Retryer.Default();
        private Logger logger = new NoOpLogger();
        private Encoder encoder = new Encoder.Default();
        private Decoder decoder = new Decoder.Default();
        private QueryMapEncoder queryMapEncoder = new FieldQueryMapEncoder();
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        private Options options = new Options();
        private InvocationHandlerFactory invocationHandlerFactory =
            new InvocationHandlerFactory.Default();
        private boolean decode404;
        private boolean closeAfterDecode = true;
        private ExceptionPropagationPolicy propagationPolicy = NONE;
        private boolean forceDecoding = false;
        private List<Capability> capabilities = new ArrayList<>();
        
        public <T> T target(Target<T> target) {
            // Bingo 这里实际调用的是ReflectiveFeign.newInstance(...)
            return build().newInstance(target);
        }
        
        public Feign build() {
            Client client = Capability.enrich(this.client, capabilities);
            Retryer retryer = Capability.enrich(this.retryer, capabilities);
            List<RequestInterceptor> requestInterceptors = this.requestInterceptors.stream()
                .map(ri -> Capability.enrich(ri, capabilities))
                .collect(Collectors.toList());
            Logger logger = Capability.enrich(this.logger, capabilities);
            Contract contract = Capability.enrich(this.contract, capabilities);
            Options options = Capability.enrich(this.options, capabilities);
            Encoder encoder = Capability.enrich(this.encoder, capabilities);
            Decoder decoder = Capability.enrich(this.decoder, capabilities);
            InvocationHandlerFactory invocationHandlerFactory =
              Capability.enrich(this.invocationHandlerFactory, capabilities);
            QueryMapEncoder queryMapEncoder = Capability.enrich(this.queryMapEncoder, capabilities);
    
            SynchronousMethodHandler.Factory synchronousMethodHandlerFactory =
                new SynchronousMethodHandler.Factory(client, retryer, requestInterceptors, logger,
                  logLevel, decode404, closeAfterDecode, propagationPolicy, forceDecoding);
            ParseHandlersByName handlersByName =
                new ParseHandlersByName(contract, options, encoder, decoder, queryMapEncoder,
                  errorDecoder, synchronousMethodHandlerFactory);
            return new ReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
        }
    }
}
~~~
#### ReflectiveFeign
创建代理类。
~~~java
public class ReflectiveFeign extends Feign {
    public <T> T newInstance(Target<T> target) {
        // 此处target 是 new HardCodedTarget<>(type, name, url)，参见FeignClientFactoryBean.getTarget()
        // 此处targetToHandlersByName 是 new ParseHandlersByName(...)，参见Feign.Builder.build()
        Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
        Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<Method, MethodHandler>();
        List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<DefaultMethodHandler>();
        // target.type() 定义的接口类
        for (Method method : target.type().getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            } else if (Util.isDefault(method)) {
                // 接口默认方法 (JDK 8 增加的接口默认实现）
                DefaultMethodHandler handler = new DefaultMethodHandler(method);
                defaultMethodHandlers.add(handler);
                methodToHandler.put(method, handler);
            } else {
                methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
            }
        }
        // new InvocationHandlerFactory.Default()
        InvocationHandler handler = factory.create(target, methodToHandler);
        // Bingo 创建代理类
        T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(),
            new Class<?>[] {target.type()}, handler);
        
        for (DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
            defaultMethodHandler.bindTo(proxy);
        }
        return proxy;
    }
}
~~~

#### InvocationHandlerFactory.Default
创建InvocationHandler
~~~java
public interface InvocationHandlerFactory {
    static final class Default implements InvocationHandlerFactory {
    
        @Override
        public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
          return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
        }
    }
}
~~~
#### ReflectiveFeign.FeignInvocationHandler
实现代理类调用
~~~java
public class ReflectiveFeign extends Feign {
    static class FeignInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("equals".equals(method.getName())) {
                try {
                    Object otherHandler =
                        args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                    return equals(otherHandler);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            } else if ("hashCode".equals(method.getName())) {
                return hashCode();
            } else if ("toString".equals(method.getName())) {
                return toString();
            }
            // Bingo 接口方法调用实际执行的是 MethodHandler，MethodHandler的创建参见 targetToHandlersByName.apply(target)
            return dispatch.get(method).invoke(args);
        }
    }
}
~~~

#### ReflectiveFeign.ParseHandlersByName
根据接口方法定义以及注解，解析生成MethodHandler。
~~~java
public class ReflectiveFeign extends Feign {
    static final class ParseHandlersByName {
        public Map<String, MethodHandler> apply(Target target) {
            // 此处contract 是 SpringMvcContract。参见FeignClientsConfiguration.feignContract(...)
            List<MethodMetadata> metadata = contract.parseAndValidateMetadata(target.type());
            Map<String, MethodHandler> result = new LinkedHashMap<String, MethodHandler>();
            for (MethodMetadata md : metadata) {
                BuildTemplateByResolvingArgs buildTemplate;
                if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
                    buildTemplate =
                        new BuildFormEncodedTemplateFromArgs(md, encoder, queryMapEncoder, target);
                } else if (md.bodyIndex() != null) {
                    buildTemplate = new BuildEncodedTemplateFromArgs(md, encoder, queryMapEncoder, target);
                } else {
                    buildTemplate = new BuildTemplateByResolvingArgs(md, queryMapEncoder, target);
                }
                if (md.isIgnored()) {
                    result.put(md.configKey(), args -> {
                        throw new IllegalStateException(md.configKey() + " is not a method handled by feign");
                    });
                } else {
                    // factory 是SynchronousMethodHandler.Factory。参见Feign.Builder.build()
                    result.put(md.configKey(), factory.create(target, md, buildTemplate, options, decoder, errorDecoder));
                }
            }
            return result;
        }
    }
}
~~~
#### SpringMvcContract
实现接口注解解析

#### SynchronousMethodHandler.Factory
创建MethodHandler
~~~java
final class SynchronousMethodHandler implements MethodHandler {
    static class Factory {
        public MethodHandler create(Target<?> target,
                                        MethodMetadata md,
                                        RequestTemplate.Factory buildTemplateFromArgs,
                                        Options options,
                                        Decoder decoder,
                                        ErrorDecoder errorDecoder) {
            return new SynchronousMethodHandler(target, client, retryer, requestInterceptors, logger,
                      logLevel, md, buildTemplateFromArgs, options, decoder,
                      errorDecoder, decode404, closeAfterDecode, propagationPolicy, forceDecoding);
        }
    }
}
~~~
#### SynchronousMethodHandler
实现Http通讯
~~~java
final class SynchronousMethodHandler implements MethodHandler {
    @Override
    public Object invoke(Object[] argv) throws Throwable {
        // 调用executeAndDecode(...)
    }
    
    Object executeAndDecode(RequestTemplate template, Options options) throws Throwable {
        // 实现HTTP通讯
    }
}
~~~

#### 小结

## 总结
