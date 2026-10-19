package com.tetra.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS SDK v2 클라이언트. 자격 증명은 기본 자격 증명 체인(ECS에서는 태스크 역할)을 쓴다. 코드 · 설정에 키를 넣지 않는다.
 * 로컬(profile local)에서는 AWS를 부르지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class AwsConfig {

	@Bean(destroyMethod = "close")
	S3Client s3Client(TetraProperties properties) {
		return S3Client.builder().region(Region.of(properties.aws().region())).build();
	}

}
