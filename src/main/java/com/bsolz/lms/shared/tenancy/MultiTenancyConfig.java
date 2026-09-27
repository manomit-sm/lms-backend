package com.bsolz.lms.shared.tenancy;

import javax.sql.DataSource;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Wires schema-per-tenant routing:
 * <ul>
 * <li>Hibernate: {@link SchemaMultiTenantConnectionProvider} + {@link TenantIdentifierResolver},
 * passed as live objects via a {@link HibernatePropertiesCustomizer} (class-name properties can't
 * inject the DataSource).</li>
 * <li>Everything else using the DataSource: the auto-configured pool is wrapped in a
 * {@link TenantAwareDataSource}.</li>
 * <li>Async work: {@link TenantTaskDecorator}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class MultiTenancyConfig {

	@Bean
	HibernatePropertiesCustomizer multiTenancyHibernatePropertiesCustomizer(
			SchemaMultiTenantConnectionProvider connectionProvider, TenantIdentifierResolver tenantIdentifierResolver) {
		return properties -> {
			properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
			properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
		};
	}

	@Bean
	static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				return bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)
						? new TenantAwareDataSource(dataSource)
						: bean;
			}
		};
	}

	@Bean
	TaskDecorator tenantTaskDecorator() {
		return new TenantTaskDecorator();
	}

}
