
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


