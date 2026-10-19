package com.tetra.portal.external;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.config.TetraProperties;

/**
 * 테넌트 감사 로그 버킷(tetra-audit-logs-{env}-{tenant_id}) 파일의 다운로드 링크 — 발급 이력 CSV · 리포트(플랜 D22).
 * DB에는 경로만 있고, 요청할 때마다 presigned GET URL을 새로 만든다. 파일은 서버를 거치지 않는다.
 */
@Component
public class AuditFileLinks {

	private static final Logger log = LoggerFactory.getLogger(AuditFileLinks.class);

	private final DownloadUrlSigner signer;

	private final TetraProperties properties;

	AuditFileLinks(DownloadUrlSigner signer, TetraProperties properties) {
		this.signer = signer;
		this.properties = properties;
	}

	public record Link(String url, OffsetDateTime expiresAt) {

		@Override
		public String toString() {
			return "Link[url=****, expiresAt=" + expiresAt + "]";
		}

	}

	/** @param downloadFileName 저장될 파일 이름(ASCII) */
	public Link link(String tenantId, String path, String downloadFileName) {
		Location location = Location.parse(path, properties.auditBucket(tenantId));
		var signed = signer.presignGet(location.bucket(), location.key(), properties.csv().presignTtl(),
				downloadFileName);
		return new Link(signed.url(), signed.expiresAt().truncatedTo(ChronoUnit.SECONDS).atZone(Kst.ZONE).toOffsetDateTime());
	}

	/**
	 * 경로 형식은 아직 미정(계획서 Q8) — "s3://버킷/키"와 키만 둘 다 받는다(Console과 같음).
	 * 단, 버킷은 반드시 로그인한 테넌트의 감사 버킷이어야 한다. 태스크 역할은 모든 테넌트 감사 버킷을 읽을 수 있으므로,
	 * DB 값이 잘못 들어가도 다른 테넌트 파일의 링크를 만들지 않게 여기서 막는다(500).
	 */
	record Location(String bucket, String key) {

		static Location parse(String path, String ownBucket) {
			String bucket = ownBucket;
			String key = path;
			if (path.startsWith("s3://")) {
				String rest = path.substring("s3://".length());
				int slash = rest.indexOf('/');
				if (slash <= 0 || slash == rest.length() - 1) {
					throw new IllegalStateException("감사 버킷 파일 경로 형식 오류");
				}
				bucket = rest.substring(0, slash);
				key = rest.substring(slash + 1);
			}
			if (!bucket.equals(ownBucket)) {
				log.error("파일 경로가 다른 버킷을 가리킵니다: expected={}, actual={}", ownBucket, bucket);
				throw new IllegalStateException("감사 버킷 불일치");
			}
			return new Location(bucket, key.startsWith("/") ? key.substring(1) : key);
		}

	}

}
