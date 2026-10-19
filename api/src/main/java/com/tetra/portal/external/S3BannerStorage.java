package com.tetra.portal.external;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tetra.portal.config.TetraProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** 테넌트 콘텐츠 버킷 tetra-storage-{env}-{tenant_id} (WF5가 만든 버킷 — Portal은 만들지 않는다). */
@Component
@Profile("!local")
class S3BannerStorage implements BannerStorage {

	private final S3Client s3;

	private final TetraProperties properties;

	S3BannerStorage(S3Client s3, TetraProperties properties) {
		this.s3 = s3;
		this.properties = properties;
	}

	@Override
	public String put(String tenantId, Long eventId, BannerImage image) {
		String key = BannerStorage.key(eventId, image);
		s3.putObject(b -> b.bucket(properties.storageBucket(tenantId)).key(key).contentType(image.contentType()),
				RequestBody.fromBytes(image.bytes()));
		return key;
	}

}
