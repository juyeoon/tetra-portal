package com.tetra.portal.external;

import java.time.Duration;
import java.time.Instant;

/** S3 presigned GET URL. 요청할 때마다 새로 만든다(논리 ERD history_file_path · 플랜 D22). 운영은 S3, 로컬은 가짜. */
public interface DownloadUrlSigner {

	/**
	 * @param downloadFileName 링크를 열면 이 이름의 파일로 저장되게 한다(Content-Disposition: attachment). ASCII만
	 */
	SignedUrl presignGet(String bucket, String key, Duration ttl, String downloadFileName);

	record SignedUrl(String url, Instant expiresAt) {

		@Override
		public String toString() {
			return "SignedUrl[url=****, expiresAt=" + expiresAt + "]";
		}

	}

}
