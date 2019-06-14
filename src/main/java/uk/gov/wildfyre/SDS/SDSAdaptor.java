package uk.gov.wildfyre.SDS;

import ca.uhn.fhir.context.FhirContext;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import uk.gov.wildfyre.SDS.support.CorsFilter;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import java.util.Collections;
import java.util.Properties;


@SpringBootApplication
@EnableSwagger2
public class SDSAdaptor {

    @Autowired
    ApplicationContext context;

    private static final Logger log = LoggerFactory.getLogger(SDSAdaptor.class);


    public static void main(String[] args) {
        System.setProperty("hawtio.authenticationEnabled", "false");
        System.setProperty("management.security.enabled","false");
        System.setProperty("management.contextPath","");
        SpringApplication.run(SDSAdaptor.class, args);

    }

    @Bean
    public ServletRegistrationBean ServletRegistrationBean() {
        ServletRegistrationBean registration = new ServletRegistrationBean(new RestfulServer(context), "/STU3/*");
        registration.setName("FhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    CorsConfigurationSource
    corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", new CorsConfiguration().applyPermitDefaultValues());
        return source;
    }

    @Bean
    @Primary
    public FhirContext FhirContextBean() {
        return FhirContext.forDstu3();
    }


    @Bean
    LdapContextSource ldapContextSource() throws NamingException{

        LdapContextSource context = new LdapContextSource();
        context.setUrl("ldap://192.168.128.11:389");
        context.setBase("o=nhs");
        return context;
    }

    @Bean
    LdapTemplate ldapTemplate(LdapContextSource context) {
        LdapTemplate template = new LdapTemplate();
        template.setContextSource(context);
        return template;
    }

    @Bean
    CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {

            @Override
            public void beforeApplicationStart(CamelContext camelContext) {

                camelContext.setNameStrategy(new DefaultCamelContextNameStrategy("SDS-LDAP"));

                final org.apache.camel.impl.SimpleRegistry registry = new org.apache.camel.impl.SimpleRegistry();
                final org.apache.camel.impl.CompositeRegistry compositeRegistry = new org.apache.camel.impl.CompositeRegistry();
                compositeRegistry.addRegistry(camelContext.getRegistry());
                compositeRegistry.addRegistry(registry);
                ((org.apache.camel.impl.DefaultCamelContext) camelContext).setRegistry(compositeRegistry);

                Properties
                        props = new Properties();
                props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                props.setProperty(Context.PROVIDER_URL, "ldap://192.168.128.11:389");
                props.setProperty(Context.URL_PKG_PREFIXES, "com.sun.jndi.url");
                props.setProperty(Context.REFERRAL, "ignore");


                try {
                    registry.put("fhirldap", new InitialLdapContext(props, null));
                } catch (NamingException ex) {
                    log.error(ex.getMessage());
                }

            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {

            }
        };
    }

    /*

     <bean id="contextSource"
    class="org.springframework.ldap.transaction.compensating.manager.TransactionAwareContextSourceProxy">
      <constructor-arg ref="contextSourceTarget" />
   </bean>


     */


    @Bean
    public FilterRegistrationBean corsFilter() {

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter());
        bean.setOrder(0);
        return bean;
    }

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("uk.gov"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(getApiInformation());
    }

    private ApiInfo getApiInformation(){
        return new ApiInfo("Demo REST API",
                "This is a Demo API created using Spring Boot",
                "1.0",
                "API Terms of Service URL",
                new Contact("Progressive Coder", "www.progressivecoder.com", "coder.progressive@gmail.com"),
                "API License",
                "API License URL",
                Collections.emptyList()
        );
    }


}
