package com.tetra.portal.config;

import javax.sql.DataSource;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tetra.portal.tenancy.SchemaMultiTenantConnectionProvider;
import com.tetra.portal.tenancy.SchemaTenantIdentifierResolver;

@Configuration(proxyBeanMethods = false)
public class JpaMultiTenancyConfig {

	@Bean
	HibernatePropertiesCustomizer multiTenancyCustomizer(DataSource dataSource) {
		var provider = new SchemaMultiTenantConnectionProvider(dataSource);
		var resolver = new SchemaTenantIdentifierResolver();
		return properties -> {
			properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, provider);
			properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
		};
	}

}
