package com.tetra.portal.external;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/** 태스크 역할(감사 버킷 GetObject)로 서명한다 — 키 없음. */
@Component
@Profile("!local")
class S3DownloadUrlSigner implements DownloadUrlSigner {

	private final S3Presigner presigner;

	S3DownloadUrlSigner(S3Presigner presigner) {
		this.presigner = presigner;
	}

	@Override
	public SignedUrl presignGet(String bucket, String key, Duration ttl, String downloadFileName) {
		// 브라우저가 CSV를 화면에 띄우지 않고 파일로 저장하도록 응답 헤더를 서명에 넣는다
		String disposition = "attachment; filename=\"" + downloadFileName + "\"";
		PresignedGetObjectRequest presigned = presigner.presignGetObject(b -> b.signatureDuration(ttl)
			.getObjectRequest(g -> g.bucket(bucket).key(key).responseContentDisposition(disposition)));
		return new SignedUrl(presigned.url().toString(), presigned.expiration());
	}

}
