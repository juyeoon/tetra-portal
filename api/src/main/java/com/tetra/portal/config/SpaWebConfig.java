package com.tetra.portal.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 화면(web/)과 API를 한 이미지로 서빙한다. 화면 빌드 결과(web/dist)는 api 빌드가 classpath:static/에 넣는다.
 * - 있는 파일(index.html, assets/*.js 등)은 그대로 돌려준다.
 * - /api/** 와 확장자가 있는 경로는 넘기지 않는다(없으면 404).
 * - 그 밖의 경로(/events/1043 같은 React 라우터 주소)는 index.html을 돌려줘 새로고침·직접 진입을 처리한다.
 * index.html이 없으면(API만 띄운 로컬 개발) 아무것도 넘기지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

	static final String STATIC_LOCATION = "classpath:/static/";

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC_LOCATION)
			.resourceChain(true)
			.addResolver(new SpaFallbackResolver());
	}

	static class SpaFallbackResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource requested = location.createRelative(resourcePath);
			if (requested.exists() && requested.isReadable()) {
				return requested;
			}
			if (!isSpaRoute(resourcePath)) {
				return null;
			}
			Resource index = location.createRelative("index.html");
			return index.exists() && index.isReadable() ? index : null;
		}

		/** API 경로가 아니고, 마지막 경로 조각에 확장자(.)가 없으면 화면 라우터 주소로 본다. */
		static boolean isSpaRoute(String resourcePath) {
			if (resourcePath.startsWith("api/") || resourcePath.equals("api") || resourcePath.startsWith("actuator")) {
				return false;
			}
			String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
			return !lastSegment.contains(".");
		}

	}

}
