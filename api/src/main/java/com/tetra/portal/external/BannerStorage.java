package com.tetra.portal.external;

/**
 * 배너를 테넌트 콘텐츠 버킷에 올린다. 키: assets/banners/{event_id}/banner.{png|jpg} (태스크 역할 권한이 이 경로만).
 * 운영은 S3, 로컬(profile local)은 파일, 시험은 목.
 */
public interface BannerStorage {

	/** 올린 객체의 키(event.banner_image_path에 넣는 값). 실패하면 예외. */
	String put(String tenantId, Long eventId, BannerImage image);

	static String key(Long eventId, BannerImage image) {
		return "assets/banners/" + eventId + "/banner." + image.extension();
	}

}
