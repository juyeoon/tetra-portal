package com.tetra.portal.external;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬(profile local) — AWS를 부르지 않는다. 받을 수는 없는 가짜 주소(어느 버킷 · 키를 서명했는지 확인용)를 돌려준다.
 * 화면은 실제 다운로드 대신 응답 모양만 확인한다.
 */
@Component
@Profile("local")
class LocalDownloadUrlSigner implements DownloadUrlSigner {

	private final Clock clock;

	LocalDownloadUrlSigner(Clock clock) {
		this.clock = clock;
	}

	@Override
	public SignedUrl presignGet(String bucket, String key, Duration ttl, String downloadFileName) {
		return new SignedUrl("https://" + bucket + ".s3.local.invalid/" + key + "?local-presigned=" + downloadFileName,
				clock.instant().plus(ttl));
	}

}
