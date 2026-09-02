package com.dongqh.luckyhub.demo;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

@TestConfiguration(proxyBeanMethods = false)
class DemoSchedulingDisabledTestConfiguration {

    @Bean
    static BeanDefinitionRegistryPostProcessor removeScheduledAnnotationProcessor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
                    throws BeansException {
                String beanName = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                // No bean instances need modification after the definition has been removed.
            }
        };
    }
}
