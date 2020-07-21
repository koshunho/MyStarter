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
		* [整合JDBC](#整合jdbc)
		* [Servlet](#servlet)
		* [Mybatis](#mybatis)
		* [Shiro](#shiro)


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
注意`BeanFactoryUtils`这个类！[BeanFactoryUtils](https://www.jianshu.com/p/3fe1c19c96ca)

beansOfTypeIncludingAncestors方法是获取包含祖先在内的特定类型的bean实例map（key为bean名称，value为bean实例），同时指定是否包含单例，以及是否允许预初始化。

简而言之`BeanFactoryUtils`这个类封装了很多对bean工厂中操作的方法，当然也就可以获取包含祖先在内的特定类型的bean实例。

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

##### 国际化
怎么写配置文件就不说了

1. 怎么在页面获取国际化的值？
   查看Thymeleaf文档，可以看到message的取值为：#{...}
![#{...}](http://qcorkht4q.bkt.clouddn.com/blog1594925284255.png)

2. 根据按照自动切换中英文

	Spirng中有一个**Locale对象**和一个**LocaleResolver解析器**。如果我们想要我们的国际化资源生效，就需要让我们自己的Locale生效。
	
	那我们自己写一个自己的LocaleResolver，并在链接上携带地区信息。
```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(prefix = "spring.mvc", name = "locale")
public LocaleResolver localeResolver() {
        // 容器中没有就自己配，有的话就用用户配置的
		if (this.mvcProperties.getLocaleResolver() == WebMvcProperties.LocaleResolver.FIXED) {
			return new FixedLocaleResolver(this.mvcProperties.getLocale());
		}
		AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
		localeResolver.setDefaultLocale(this.mvcProperties.getLocale());
		return localeResolver;
		}
   ```
AcceptHeaderLocaleResolver有这样一个方法 --> **解析Locale**，我们也要重写这个方法来解析Locale
```java
@Override
public Locale resolveLocale(HttpServletRequest request) {
		Locale defaultLocale = getDefaultLocale();
		// 根据请求头带来的区域信息获取Locale进行国际化
		if (defaultLocale != null && request.getHeader("Accept-Language") == null) {
			return defaultLocale;
		}
		Locale requestLocale = request.getLocale();
		List<Locale> supportedLocales = getSupportedLocales();
		if (supportedLocales.isEmpty() || supportedLocales.contains(requestLocale)) {
			return requestLocale;
		}
		Locale supportedLocale = findSupportedLocale(request, supportedLocales);
		if (supportedLocale != null) {
			return supportedLocale;
		}
		return (defaultLocale != null ? defaultLocale : requestLocale);
	}
```
---
我们修改一下前端页面，附带地区信息。之前都是注意/index.html?language=xx&&a=1 这样的形式，而thymeleaf的语法，用`()`传参
```html
			<a class="btn btn-sm" th:href="@{/index.html(language='zh_CN')}">中文</a>
			<a class="btn btn-sm" th:href="@{/index.html(language='en_US')}">English</a>
```

自己写一个组件类，我们**重写resolveLocale方法解析Locale**
```java
public class MyLocaleResolver implements LocaleResolver {

    //解析请求
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String language = request.getParameter("language");

        Locale locale = Locale.getDefault();  //如果没有就使用默认的

        //如果请求的链接携带了国际化的参数
        if(!StringUtils.isEmpty(language)){
            String[] split = language.split("_");
            locale = new Locale(split[0],split[1]);
        }
        return locale;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {

    }
}
```

为了让MyLocaleResolver生效，我们要配置类中把它注入容器
```java
    //必须把自定义的组件放在配置类@bean，自定义的国际化组件才生效
    @Bean
    public LocaleResolver localeResolver(){
        return new MyLocaleResolver();
    }
```

##### 拦截器

解决不用登陆也可以直接访问到后台主页

1.登陆时，把登录信息放进session
```java
    @RequestMapping("/user/login")
    public String login(@RequestParam("Username") String username,
                        @RequestParam("Password") String pwd,
                        Model model, HttpSession session){
        if(!StringUtils.isEmpty(username) && pwd.equals("123")){
            session.setAttribute("loginUser",username);
            return "dashboard";
        }else{
            model.addAttribute("loginMsg","用户名or密码错误！");
            return "index";
        }
    }
	
	@RequestMapping("/user/logout")
    public String logout(HttpSession session){
        session.invalidate();
        return "redirect:/index.html";
    }
```
2. 定义一个拦截器实现HandlerInterceptor接口，只需要重写preHandle方法
```java
public class LoginHandlerInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object loginUser = request.getSession().getAttribute("loginUser");
        if(loginUser == null){
            request.setAttribute("loginMsg","没有权限");
            request.getRequestDispatcher("test.html").forward(request,response);
            return false;
        }else{
            return true;
        }
    }
}
```
	
　　试图从session中获取登陆信息，如果为空，就返回false，不放行

3. 然后把拦截器注册到实现WebMvcConfiguer的配置类中
```java
@Configuration
public class MyConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginHandlerInterceptor()).
                addPathPatterns("/**").
                excludePathPatterns("/t1","/","login");
    }
}
```
　　需要调用excludePathPatterns，指定哪些请求不该拦截（登录、静态志愿、首页..）
  
  4. 在主页可以获取用户登录的信息
 ```html
	 [[${session.loginUser}]]
 ```
 
 ##### Thymeleaf 公共页面抽取
 
 步骤：
 1. 抽取公共片段th:fragment 定义模板名
 2. 引入公共片段th:insert插入模板名
 3. 如果要传递参数，可以直接使用()传参，接收判断即可
    
单独建一个**common.html**，我们将头部nav标签抽取定义为一个模板
  ```html
  <!--头部导航栏-->
<nav class="navbar navbar-dark sticky-top bg-dark flex-md-nowrap p-0" th:fragment="topbar">
    <a class="navbar-brand col-sm-3 col-md-2 mr-0" href="">[[${session.loginUser}]]</a>
    <input class="form-control form-control-dark w-100" type="text" placeholder="Search" aria-label="Search">
    <ul class="navbar-nav px-3">
        <li class="nav-item text-nowrap">
            <a class="nav-link" th:href="@{/user/logout}">注销</a>
        </li>
    </ul>
</nav>
  ```

然后我们在别的页面中引入，删掉原来的nav
```html
<!--头部导航栏-->
<div th:replace="~{commons/commons::topbar}"></div>
```

语法：`th:insert=~{模板::标签名}`

p.s.可以使用th:insert, th:replace, th:include

 ##### 整合JDBC
1. 导入依赖
```xml
 <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <scope>runtime</scope>
</dependency>
 ```
 2. 编写yaml连接数据库
```yaml
spring:
  datasource:
    username: root
    password: 123456
    url: jdbc:mysql://localhost:3306/springboot?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8
    driver-class-name: com.mysql.cj.jdbc.Driver
```
 
3. **配置完这一些东西后，我们就可以直接去使用了，因为SpringBoot已经默认帮我们进行了自动配置**
   
   直接获得数据源，获得连接
```java
@SpringBootTest
class SpringboottestApplicationTests {

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoads() throws SQLException {
        System.out.println(dataSource.getClass());

        Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from springboot.employee");
        while(resultSet.next()){
            System.out.println(resultSet.getString("lastName"));
        }
        resultSet.close();
        statement.close();
        connection.close();

    }

}
```

有了数据库连接，**就可以** CRUD 操作数据库了。但是SpringBoot帮我们封装好了一个对象JdbcTemplate
![JdbcTemplateConfiguration](http://qcorkht4q.bkt.clouddn.com/blog1594994695152.png)

可以看到这个bean只需要注入一个dataSource和JdpcProperties就可以了，dataSource已经由SpringBoot搞定了，JdpcProperties我们自己配置了。就可以直接在容器中获取到这个bean了。

- 有了数据源(com.zaxxer.hikari.HikariDataSource)，然后可以拿到数据库连接(java.sql.Connection)，有了连接，就可以使用原生的 JDBC 语句来操作数据库；

- 即使不使用第三方数据库操作框架，如 MyBatis等，Spring 本身也**对原生的JDBC 做了轻量级的封装**，即JdbcTemplate。

- 数据库操作的所有 CRUD 方法都在 JdbcTemplate 中。

- Spring Boot 不仅提供了默认的数据源，同时默认已经配置好了 JdbcTemplate 放在了容器中，程序员只需自己注入即可使用
  
 ```java
@RestController
public class JDBCController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    //查询数据库的所有信息
    //没有实体类，用Map来获取信息 Map<字段名，信息>
    @GetMapping("/userList")
    public List<Map<String,Object>> userList(){
        String sql = "select * from springboot.employee";
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        return maps;
    }
	
	 //修改用户信息
    @GetMapping("/update/{id}")
    public String updateUser(@PathVariable("id") int id){
        //插入语句
        String sql = "update springboot.employee set lastName=?,email=? where id="+id;
        //数据
        Object[] objects = new Object[2];
        objects[0] = "大天狗";
        objects[1] = "123@qq.com";
        jdbcTemplate.update(sql,objects);
        //查询
        return "OK";
    }
}  
 ```
 
 注意上面的updateUser方法 用`?`占位的方式
 
  ##### Servlet
一般Web开发使用 Controller 基本上可以完成大部分需求，但是有的时候我们还是会用到 Servlet。

内置 Servlet 容器时没有web.xml文件，所以使用 Spring Boot 的注册 Servlet 方式 `ServletRegistrationBean`

1. 写一个Servlet
```java
public class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().print("<h1>I am TestServlet</h1>");
    }
}
```
　　一般来说我们是用不到doGet方法的，doGet方法提交表单的时候会在url后边显示提交的内容，所以不安全。
  
   而且doGet方法只能提交256个字符(1024字节)，而doPost没有限制，因为get方式数据的传输载体是URL（提交方式能form，也能任意的URL链接），而POST是HTTP头键值对（只能以form方式提交）。
通常我们使用的都是doPost方法，你只要在servlet中让这两个方法互相调用就行了。

   servlet碰到doGet方法调用直接就会去调用doPost因为他们的参数都一样。而且doGet方法处理中文问题很困难，要写过滤器之类的。

2. 再通过ServletRegistrationBean注册
```java
@Configuration
public class Config {
    @Bean
    public ServletRegistrationBean testServletRegistration(){
        ServletRegistrationBean<TestServlet> bean = new ServletRegistrationBean<>(new TestServlet());
        bean.addUrlMappings("/testServlet");
        return bean;
    }
}
```
![TestServlet](http://qcorkht4q.bkt.clouddn.com/blog1595001518470.png)

Druid数据源监控就是提供了一个 web 界面方便用户查看。采用了ServletRegistrationBean配置 Druid 监控管理后台的Servlet。[配置_StatViewServlet配置](https://github.com/alibaba/druid/wiki/%E9%85%8D%E7%BD%AE_StatViewServlet%E9%85%8D%E7%BD%AE)

##### Mybatis

1. 引入相关依赖
   
   注意，这不是Spring官方的
```xml
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.1.1</version>
        </dependency>
```
2. 创建mapper目录和对应的Mapper接口
```java
@Mapper
@Repository
public interface DepartmentMapper {
    List<Department> selectAllDepartment();

    Department selectDepartmentById(@Param("id") int id);
}
```
添加了@Mapper注解之后这个接口在编译时会生成相应的实现类，也就是使用了动态代理！

当映射器方法需要多个参数时，@Param可以被用于给映射器方法中的每个参数来取一个名字。否则，多参数将会以它们的**顺序位置和SQL语句中的表达式进行映射**，这是默认的。

若使用@Param(“id”)，则SQL中参数应该被命名为：#{id}。
[Mybatis传递多个参数的4种方式](https://mp.weixin.qq.com/s?__biz=MzI3ODcxMzQzMw==&mid=2247485137&idx=1&sn=8b6fa49dccc01b040c24749375e88a4f&chksm=eb5383e7dc240af15943519938f33da919436464b3182a70fec0ed3196a8761bf85594c6c0e6&scene=21#wechat_redirect)


2. 对应的Mapper.xml文件

    **注意namespace**
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.huang.mapper.DepartmentMapper">
    <select id="selectAllDepartment" resultType="department">
        SELECT * FROM springboot.department
    </select>

    <select id="selectDepartmentById" resultType="department">
        select *
        from springboot.department
        where id = #{id};
    </select>
</mapper>
```

3. 配置别名和接口扫描
```yaml
mybatis:
  type-aliases-package: com.huang.pojo
  mapper-locations: classpath:mapper/*.xml
```
##### Shiro
![Shiro](http://qcorkht4q.bkt.clouddn.com/blogshiro.png)

1. 设置Realm
```java
public class UserRealm extends AuthorizingRealm {

    @Autowired
    UserService userService;

    //授权
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        //拿到当前登录的对象
        Subject subject = SecurityUtils.getSubject();

        //这里就是从AuthenticationInfo取到当前的对象principal
        // 因为把user作为第一个参数传递过来了
        User currentUser = (User) subject.getPrincipal(); //拿到user对象

        //设置当前用户的权限
        HashSet<String> set = new HashSet<>();
        set.add(currentUser.getRole());
        info.setRoles(set);

        //return info
        return info;
    }

    //认证
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        System.out.println("执行了认证方法");

        //这些类是有联系的，controller里面封装了token，就是一个全局的，都可以调得到
        UsernamePasswordToken userToken = (UsernamePasswordToken) token;

        //在执行登录的时候，就会走到这个方法
        //用户名 密码 从数据库中取
        User user = userService.queryUserByName(userToken.getUsername());

        if(user == null){
            // 抛出异常 UnknownAccountException
            return null;
        }

        Object principal = user;

        Object credentials = user.getPassword();

        ByteSource salt = ByteSource.Util.bytes(user.getUsername());

        String realmName = getName();

        //密码认证，shiro做
        return new SimpleAuthenticationInfo(principal,credentials,salt,realmName);
    }
}
```

2. 设置ShiroConfig

    **HashedCredentialsMatcher** -> **Realm** -> **DefaultWebSecurityManager** -> **ShiroFilterFactoryBean**
```java
@Configuration
public class ShiroConfig {
    //ShiroFilterFactoryBean
    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(@Qualifier("defaultWebSecurityManager") DefaultWebSecurityManager defaultWebSecurityManager){
        ShiroFilterFactoryBean bean = new ShiroFilterFactoryBean();

        bean.setSecurityManager(defaultWebSecurityManager);

        //添加shiro的内置过滤器
        /*anon:无须认证就可以访问
        * authc：必须认证了才能访问
        * user：必须拥有 记住我 功能才能使用
        * perms：拥有对某个资源的权限才能访问
        * role：拥有某个角色权限才能访问*/
        LinkedHashMap<String, String> filterMap = new LinkedHashMap<>();

        filterMap.put("/","anon");
        filterMap.put("/index","anon");
        filterMap.put("/login","anon");
        filterMap.put("/toLogin","anon");
        filterMap.put("/toRegister","anon");
        filterMap.put("/doRegister","anon");


        filterMap.put("/user/add","roles[admin]");
        filterMap.put("/user/update","roles[user]");

        filterMap.put("/logout","logout");

        filterMap.put("/**","authc");

        bean.setFilterChainDefinitionMap(filterMap);

        //设置登录的请求
        bean.setLoginUrl("/toLogin");

        bean.setUnauthorizedUrl("/notRole");

        return bean;
    }

    //DefaultWebSecurityManager
    @Bean
    public DefaultWebSecurityManager defaultWebSecurityManager(@Qualifier("userRealm") UserRealm userRealm){
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();

        securityManager.setRealm(userRealm);

        return securityManager;
    }

    //创建realm对象，需要自定义类
    @Bean
    public UserRealm userRealm(@Qualifier("hashedCredentialsMatcher") HashedCredentialsMatcher matcher){
        UserRealm userRealm = new UserRealm();

        userRealm.setAuthorizationCachingEnabled(false);

        userRealm.setCredentialsMatcher(matcher);

        return userRealm;
    }

    //密码匹配凭证管理器
    @Bean
    public HashedCredentialsMatcher hashedCredentialsMatcher() {
        HashedCredentialsMatcher hashedCredentialsMatcher = new HashedCredentialsMatcher();
        // 采用MD5方式加密
        hashedCredentialsMatcher.setHashAlgorithmName("MD5");
        // 设置加密次数
        hashedCredentialsMatcher.setHashIterations(1024);

        hashedCredentialsMatcher.setStoredCredentialsHexEncoded(true);

        return hashedCredentialsMatcher;
    }
}
```