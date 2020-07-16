## SpringBoot笔记
* [SpringBoot笔记](#springboot笔记)
	* [自动装配再深入](#自动装配再深入)
	* [自己做一个starter](#自己做一个starter)
	* [MVC自动配置原理](#mvc自动配置原理)
	* [ContentNegotiatingViewResolver 内容协商视图解析器](#contentnegotiatingviewresolver-内容协商视图解析器)
	* [修改SpringBoot的默认配置](#修改springboot的默认配置)
	* [注意点](#注意点)
		* [@PropertySource](#propertysource)
		* [多环境切换](#多环境切换)
		* [Thymeleaf遍历](#thymeleaf遍历)


#### 自动装配再深入
![程序入口](http://qcorkht4q.bkt.clouddn.com/blog1594816832871.png)

进入@SpringBootApplication
![SpringBootApplication](http://qcorkht4q.bkt.clouddn.com/blog1594816927900.png)

进入@EnableAutoConfiguration

@EnableAutoConfiguration 注解会导入AutoConfigurationImportSelector类的实例被引入到Spring容器中
![EnableAutoConfiguration](http://qcorkht4q.bkt.clouddn.com/blog1594816986262.png)

进入AutoConfigurationImportSelector.class
![AutoConfigurationImportSelector](http://qcorkht4q.bkt.clouddn.com/blog1594817044704.png)

---
**AutoConfigurationImportSelector**

注意selectImports方法
```java
// AutoConfigurationImportSelector （位于package - org.springframework.boot.autoconfigure， 所以是SpringBoot自带的）
	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return NO_IMPORTS;
		}
		AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(annotationMetadata);
		return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
	}
```
里面调用了getAutoConfigurationEntry方法
```java
	protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
		// 加载 META-INF/spring-autoconfigure-metadata.properties 中的相关配置信息, 注意这主要是供Spring内部使用的
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		        // 获取所有通过META-INF/spring.factories配置的, 此时还不会进行过滤和筛选
        // key ： org.springframework.boot.autoconfigure.EnableAutoConfiguration
		List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
		// 开始对上面取到的进行过滤,去重,排序等操作
		configurations = removeDuplicates(configurations);
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		checkExcludedClasses(configurations, exclusions);
		configurations.removeAll(exclusions);
		configurations = getConfigurationClassFilter().filter(configurations);
		fireAutoConfigurationImportEvents(configurations, exclusions);
		// 这里返回的满足条件, 通过筛选的配置类 
		return new AutoConfigurationEntry(configurations, exclusions);
	}
```

进入getCandidateConfigurations方法
```java
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
	        // 获取所有通过META-INF/spring.factories配置的, 此时还不会进行过滤和筛选
			//key 为:org.springframework.boot.autoconfigure.EnableAutoConfiguration的配置的value(类路径+类名称)
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames(getSpringFactoriesLoaderFactoryClass(),
				getBeanClassLoader());
		Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you "
				+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}
	
	// 这里返回的就是EnableAutoConfiguration.class
	protected Class<?> getSpringFactoriesLoaderFactoryClass() {
		return EnableAutoConfiguration.class;
	}
```
---
**SpringFactoriesLoader**

进入loadFactoryNames方法
```java
	public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
	    //此时为org.springframework.boot.autoconfigure.EnableAutoConfiguration。Class.getName()返回类的全限定名
		String factoryTypeName = factoryType.getName();
		return loadSpringFactories(classLoader).getOrDefault(factoryTypeName, Collections.emptyList());
	}

	private static Map<String, List<String>> loadSpringFactories(@Nullable ClassLoader classLoader) {
		MultiValueMap<String, String> result = cache.get(classLoader);
		if (result != null) {
			return result;
		}

		try {
		    //配置项的默认位置META-INF/spring.factories
			Enumeration<URL> urls = (classLoader != null ?
					classLoader.getResources(FACTORIES_RESOURCE_LOCATION) :
					ClassLoader.getSystemResources(FACTORIES_RESOURCE_LOCATION));
			result = new LinkedMultiValueMap<>();
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				UrlResource resource = new UrlResource(url);
				//从多个配置文件中查找，例如我的有:spring-boot-admin-starter-client-1.5.6.jar!/META-INF/spring.factories和stat-log-0.0.1-SNAPSHOT.jar!/META-INF/spring.factories
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					String factoryTypeName = ((String) entry.getKey()).trim();
					for (String factoryImplementationName : StringUtils.commaDelimitedListToStringArray((String) entry.getValue())) {
						result.add(factoryTypeName, factoryImplementationName.trim());
					}
				}
			}
			cache.put(classLoader, result);
			return result;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}
	}
```
loadFactoryNames 方法调用 loadSpringFactories 方法，这个方法去加载了 META-INF/spring.factories 配置文件，再把这些配置文件整合到一起返回，调用了 map 的 getOrDefault 方法， 根据 key 获取 一个 list。而这个 key，正是前面通过 getSpringFactoriesLoaderFactoryClass 方法获取的 EnableAutoConfiguration.class 的全路径名: 
`org.springframework.boot.autoconfigure.EnableAutoConfiguration`。


**看上面的源码可知通过SpringFactoriesLoader.loadFactoryNames()把多个jar的/META-INF/spring.factories配置文件中的有EnableAutoConfiguration配置项都抓出来。**

![spring.factories](http://qcorkht4q.bkt.clouddn.com/blog1594818106021.png)

所以@EnableAutoConfiguration的大致原理就是从classpath中搜寻所有的META-INF/spring.factories配置文件，并将其中org.springframework.boot.autoconfigure.EnableutoConfiguration对应的配置项通过反射实例化为对应的标注了@Configuration的JavaConfig形式的IoC容器配置类，然后汇总为一个并加载到IoC容器。

#### 自己做一个starter
1. 新建一个SpringBoot项目，先修改pom.xml
```xml
<!--注释掉-->
    <!--<parent>-->
    <!--    <groupId>org.springframework.boot</groupId>-->
    <!--    <artifactId>spring-boot-starter-parent</artifactId>-->
    <!--    <version>2.3.1.RELEASE</version>-->
    <!--    <relativePath/> &lt;!&ndash; lookup parent from repository &ndash;&gt;-->
    <!--</parent>-->
	
	<!--改为dependcyManagement-->
	    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>2.3.1.RELEASE</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```
作用是顶层的POM文件中，我们会看到dependencyManagement元素。通过它元素来管理jar包的版本，让子项目中引用一个依赖而不用显示的列出版本号。Maven会沿着父子层次向上走，直到找到一个拥有dependencyManagement元素的项目，然后它就会使用在这个dependencyManagement元素中指定的版本号。

2. 添加依赖
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
```
为了用这个包下的@ConditionalOnClass

3. 添加Properties配置
```java
package com.huang.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "my-starter")
public class MyStarterProperties {
    private String name = "koshunho";

    private String projectName = "my-starter";
}

```

@ConfigurationProperties(prefix = "my-starter")的作用是后面可以通过application.properties来修改
```xml
my-starter.name = ***
my-starter.project-name = ***
```
4. 创建业务类
```java
package com.huang.starter;

import lombok.Data;

@Data
public class MyStarterService {
    private String name;

    private String projectName;

    public String getMsg(){
        return "Author is " + name + ", Project Name is " + projectName;
    }
}
```
5. 自动装配
```java
package com.huang.starter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MyStarterProperties.class)
@ConditionalOnClass(MyStarterService.class)
public class MyStarterAutoConfiguration {

    @Autowired
    private MyStarterProperties properties;

    @Bean
    public MyStarterService myStarterService(){
        MyStarterService service = new MyStarterService();

        service.setName(properties.getName());

        service.setProjectName(properties.getProjectName());

        return service;
    }
}

   ```
 `@EnableConfigurationProperties`注解的作用是：使得使用`@ConfigurationProperties`注解的类生效。
   
如果一个配置类只配置`@ConfigurationProperties`注解，而没有使用`@Component`，那么在IoC容器中是获取不到properties 配置文件转化的bean。说白了 `@EnableConfigurationProperties` 相当于把使用 `@ConfigurationProperties` 的类进行了一次注入。

5. 配置文件
在resources文件夹下创建 META-INF→spring.factories
```xml
org.springframework.boot.autoconfigure.EnableAutoConfiguration = com.huang.starter.MyStarterAutoConfiguration
```

6. 打包
   maven -> Lifecycle -> install
   
7. 测试
   本来应该新建一个SpringBoot项目，懒了
   
   先引入刚刚打好的jar包
```xml
           <dependency>
            <groupId>com.huang</groupId>
            <artifactId>my-starter</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```

写一个Controller
```java
package com.huang.controller;

import com.huang.starter.MyStarterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private MyStarterService myStarterService;

    @GetMapping("/teststarter")
    public String test(){
        return myStarterService.getMsg();
    }
}

```
![test](http://qcorkht4q.bkt.clouddn.com/blog1594825803479.png)
修改application.properties
```xml
my-starter.name = fuckyou
my-starter.projectName = xixi
```
![test](http://qcorkht4q.bkt.clouddn.com/blog1594825855647.png)


#### MVC自动配置原理
[Spring MVC Auto-configuration](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-developing-auto-configuration)

> The auto-configuration adds the following features on top of Spring’s defaults:
> 
> //包含了ContentNegotiatingViewResolver内容协商视图解析器 和 BeanNameViewResolver 解析器
> Inclusion of ContentNegotiatingViewResolver and BeanNameViewResolver beans.
> 
> Support for serving static resources, including support for WebJars (covered later in this document)).
> 
> Automatic registration of Converter, GenericConverter, and Formatter beans.
> 
> Support for HttpMessageConverters (covered later in this document).
> 
> Automatic registration of MessageCodesResolver (covered later in this document).
> 
> Static index.html support.
> 
> Custom Favicon support (covered later in this document).
> 
> Automatic use of a ConfigurableWebBindingInitializer bean (covered later in this document).
> 
> If you want to keep those Spring Boot MVC customizations and make more MVC customizations (interceptors, formatters, view controllers, and other features), you can add your own @Configuration class of type WebMvcConfigurer but without @EnableWebMvc.

如果希望保留Spring Boot MVC功能，并且希望添加其他MVC配置（拦截器、formatters，视图控制器和其他功能），则可以添加自己的`@Configuration`类，类型为`WebMvcConfigurer`，但不添加`@EnableWebMvc`。

如果想完全控制Spring MVC，可以添加自己的`@Configuration`并用`@EnableWebMvc`进行注解。

#### ContentNegotiatingViewResolver 内容协商视图解析器
这个方法同样也在`WebMvcAutoConfiguration`的`WebMvcAutoConfigurationAdapter`中。（注意，`WebMvcAutoConfigurationAdapter`就是实现了`WebMvcConfigurer`接口）

```java
public static class WebMvcAutoConfigurationAdapter implements WebMvcConfigurer {
        ...
		public ContentNegotiatingViewResolver viewResolver(BeanFactory beanFactory) {
            ContentNegotiatingViewResolver resolver = new ContentNegotiatingViewResolver();
            resolver.setContentNegotiationManager((ContentNegotiationManager)beanFactory.getBean(ContentNegotiationManager.class));
			// ContentNegotiatingViewResolver使用其他所有视图解析器来定位视图，因此它应该具有较高的优先级
            resolver.setOrder(Ordered.HIGHEST_PERCEDENCE);
            return resolver;
        }
		....
}
```
点进`ContentNegotiatingViewResolver`看看对应解析视图的代码
```java
    public View resolveViewName(String viewName, Locale locale) throws Exception {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
        List<MediaType> requestedMediaTypes = this.getMediaTypes(((ServletRequestAttributes)attrs).getRequest());
        if (requestedMediaTypes != null) {
		    // 获取候选的视图对象
            List<View> candidateViews = this.getCandidateViews(viewName, locale, requestedMediaTypes);
			// 选择一个最合适的视图对象，然后把这个对象返回
            View bestView = this.getBestView(candidateViews, requestedMediaTypes, attrs);
            if (bestView != null) {
                return bestView;
            }
        }
		...
    }
```
看看怎么获取候选的视图的
```java
    private List<View> getCandidateViews(String viewName, Locale locale, List<MediaType> requestedMediaTypes) throws Exception {
        List<View> candidateViews = new ArrayList();
        if (this.viewResolvers != null) {
            Assert.state(this.contentNegotiationManager != null, "No ContentNegotiationManager set");
            Iterator var5 = this.viewResolvers.iterator();

            while(var5.hasNext()) {
			...
}
```
是通过把所有的视图解析器拿来，进行while循环，逐个解析。

-->`ContentNegotiatingViewResolver`这个视图解析器就是用来组合所有的视图解析器的。

`ContentNegotiatingViewResolver`里面有个属性viewResolvers，看看是在哪赋值的
```java
protected void initServletContext(ServletContext servletContext) {
        // 它是从beanFacotory工具中获取容器中所有的视图解析器
		// ViewResolver.class 把所有的是解析器拿来组合
		Collection<ViewResolver> matchingBeans =
				BeanFactoryUtils.beansOfTypeIncludingAncestors(obtainApplicationContext(), ViewResolver.class).values();
		if (this.viewResolvers == null) {
			this.viewResolvers = new ArrayList<>(matchingBeans.size());
			for (ViewResolver viewResolver : matchingBeans) {
				if (this != viewResolver) {
					this.viewResolvers.add(viewResolver);
				}
			}
		}
		...
}
```

所以解析视图的流程（也就是`resolveViewName方法`）：从BeanFacotory中所有的视图解析器，封装成一个属性`List<ViewResolver> viewResolvers` --> 调用`getCandidateViews`方法，遍历所有的视图解析器以获取候选的视图对象 --> 调用 `getBestView`方法，返回一个最合适的视图对象。

既然它是在容器中去找视图解析器，所以我们要自定义一个视图解析器的话，就去容器中添加一个，`ContentNegotiatingViewResolver`就会自动的将它组合起来。

1. 自己写一个视图解析器来测试一下。
```java
@Configuration
public class MyConfig {

    @Bean
    public ViewResolver myViewResolver(){
        return new MyViewResolver();
    }


    private static class MyViewResolver implements ViewResolver{
        @Override
        public View resolveViewName(String viewName, Locale locale) throws Exception {
            return null;
        }
    }
}
```

2. 在DispatcherServlet中的doDispatch方法打个断点
   
3. 访问http://localhost:8080/看结果

![自定义视图解析器](http://qcorkht4q.bkt.clouddn.com/blog1594908789205.png)

可以看到我们自定义的视图解析器就在这了。

--> 我们如果想要定制化的东西，只需要给容器中添加这个组件就好了，剩下的事情SpringBoot会帮我们做了。

#### 修改SpringBoot的默认配置
SpringBoot在自动配置很多组件的时候，先看容器中有没有用户自己配置的（如果用户自己配置`@bean`)，如果有就用用户配置的，如果没有就用自动配置。

组件**可以存在多个**，比如视图解析器，就将用户配置和自己默认的结合起来。

**SpringBoot是怎么做到既保留所有的自动配置，又能使用我们扩展的配置呢？**
> If you want to keep those Spring Boot MVC customizations and make more MVC customizations (interceptors, formatters, view controllers, and other features), you can add your own @Configuration class of type WebMvcConfigurer but without @EnableWebMvc.

再次注意官方文档说的这句话，要想扩展MVC的功能，就实现WebMvcConfigurer接口并加上@Configuration

**@Configuration标注在类上，相当于把该类作为spring的xml配置文件中的`<beans>`，作用为：配置spring容器(应用上下文)**
![@Configuration](http://qcorkht4q.bkt.clouddn.com/blog1594919421087.png)
**@Configuration注解本身定义时被@Component标注了，因此本质上来说@Configuration也是一个@Component**

![WebMvcConfigurationAdapter](http://qcorkht4q.bkt.clouddn.com/blog1594917681994.png)

WebMvcAutoConfiguration中有一个类WebMvcConfigurationAdapter，这个类上有一个注解`@Import({WebMvcAutoConfiguration.EnableWebMvcConfiguration.class})
`

点进EnableWebMvcConfiguration这个类看一下，它继承了一个父类DelegatingWebMvcConfiguration
![EnableWebMvcConfiguration](http://qcorkht4q.bkt.clouddn.com/blog1594917857989.png)

父类中有这样一段代码
```java
public class DelegatingWebMvcConfiguration extends WebMvcConfigurationSupport {

	private final WebMvcConfigurerComposite configurers = new WebMvcConfigurerComposite();

    //从容器中获取所有的WebMvcConfigurer
	@Autowired(required = false)
	public void setConfigurers(List<WebMvcConfigurer> configurers) {
		if (!CollectionUtils.isEmpty(configurers)) {
			this.configurers.addWebMvcConfigurers(configurers);
		}
	}
```
---
注意@Autowired放在方法上时：

（1）该方法如果有参数，会使用Autowired的方式在容器中查找是否有该参数

（2）**同时会执行该方法**

因此**该方法会从容器中注入所有的WebMvcConfigurer**


---
我们实现一个实现了WebMvcConfigurer的类，重写一个addViewControllers方法，那么我们自己重写的这个方法怎么生效的呢？

继续看DelegatingWebMvcConfiguration，发现它调用了一个addViewControllers方法

```java
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
	    // 将所有的WebMvcConfigurer相关配置来一起调用！包括我们自己配置的和Spring给我们配置的
		for (WebMvcConfigurer delegate : this.delegates) {
			delegate.addViewControllers(registry);
		}
	}
```

--> 在SpringBoot中，有非常多的xxxConfigurer帮我们进行扩展配置，只要看见这个东西，就要马上**注意扩展了什么功能**！
#### 注意点

##### @PropertySource
因为SpringBoot默认从application.properties/yml等默认的获取值，要是我想自定义怎么办？

`@PropertySource(value = "classpath:xxx.properties")`: 加载指定的配置文件

这种做法必须手动对每个属性赋值，而`@ConfigurationProperties`支持批量注入配置文件中的属性。

使用EL表达式 `@Value(${name})`

--> 如果我们在业务中，只需要获取配置文件中的某个值，可以用一下`@Value`。
如果我们专门写了一个JavaBean来和配置文件进行映射，躊躇なく`@ConfigurationProperties`で使ってください

##### 多环境切换
加载优先级：
1. `–file:./config/`
2. `–file:./`
3. `–classpath:/config/`
4. `–classpath:/`

使用yml切换环境
![yml切换环境](http://qcorkht4q.bkt.clouddn.com/blog1594835436803.png)

##### Thymeleaf遍历

`th:each`

1. 写一个Controller，放一些数据
```java
@Controller
public class ControllerTest {

    //　Spring MVC 在调用方法前会创建一个隐含的模型对象作为模型数据的存储容器（事实上这个隐含的模型对象是一个BindingAwareModelMap 类型的对象）
    // 如果方法的入参为Map、Model或者ModelMap 类型，Spring MVC 会将隐含模型的引用传递给这些入参
    //（因为BindingAwareModelMap 继承或实现了Map、Model或者ModelMap）。
    @RequestMapping("/t1")
    public String test1(Map<String,Object> map){
        map.put("msg", "<h1>hello</h1>");
        map.put("users", Arrays.asList("NMSL","XIXI"));

        return "test";
    }
}
   ```
2. 从页面取出数据
```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>test</title>
</head>
<body>
<h1>测试页面</h1>

<div th:text="${msg}"></div>
<!--不转义-->
<div th:utext="${msg}"></div>

<!--遍历数据-->
<h4 th:each="user:${users}" th:text="${user}"></h4>

<h4>
    <!--行内写法，[[]]-->
    <span th:each="user:${users}">[[${user}]]</span>
</h4>
</body>
</html>
```
