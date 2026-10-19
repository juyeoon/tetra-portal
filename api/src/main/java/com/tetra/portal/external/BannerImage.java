package com.tetra.portal.external;

import java.util.Arrays;

/**
 * 검사를 통과한 배너 파일(메모리). 형식은 파일 앞부분(매직 바이트)으로 정한다 — 이름 · Content-Type은 믿지 않는다.
 * 업로드가 끝날 때까지 메모리에 들고 있으므로 상한(2MB)을 지킨다.
 */
public record BannerImage(byte[] bytes, String extension, String contentType) {

	public static final long MAX_BYTES = 2L * 1024 * 1024;

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	/** png · jpeg가 아니면 null. */
	public static BannerImage detect(byte[] bytes) {
		if (startsWith(bytes, PNG)) {
			return new BannerImage(bytes, "png", "image/png");
		}
		if (startsWith(bytes, JPEG)) {
			return new BannerImage(bytes, "jpg", "image/jpeg");
		}
		return null;
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes != null && bytes.length >= prefix.length
				&& Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
	}

	@Override
	public String toString() {
		return "BannerImage[" + extension + ", " + bytes.length + " bytes]";
	}

}
