package com.tetra.portal.external;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 실행(profile local) — S3 대신 build/local-banners/{tenant_id}/{키}에 쓴다.
 */
@Component
@Profile("local")
class LocalBannerStorage implements BannerStorage {

	private static final Logger log = LoggerFactory.getLogger(LocalBannerStorage.class);

	private static final Path ROOT = Path.of("build", "local-banners");

	@Override
	public String put(String tenantId, Long eventId, BannerImage image) {
		String key = BannerStorage.key(eventId, image);
		Path file = ROOT.resolve(tenantId).resolve(key);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, image.bytes());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		log.info("로컬 배너 저장: {}", file.toAbsolutePath());
		return key;
	}

}
