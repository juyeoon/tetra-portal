package com.tetra.portal;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.tetra.portal.common.time.Kst;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PortalApiApplication {

	public static void main(String[] args) {
		// 저장·응답 타임존은 KST. TZ 환경 변수가 빠져도 JVM 기본값을 맞춘다.
		TimeZone.setDefault(TimeZone.getTimeZone(Kst.ZONE));
		SpringApplication.run(PortalApiApplication.class, args);
	}

}
