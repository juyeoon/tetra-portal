package com.tetra.portal.account;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.tetra.portal.config.TetraProperties;

/**
 * 비밀번호 설정 링크 서명 검증 — Console(PasswordLinkSigner)이 만든 링크를 같은 규칙으로 확인한다.
 * 링크: {PORTAL_BASE_URL}/password/setup?t={tenant_id}&exp={epoch초}&sig={서명}
 * sig = base64url(패딩 없음, HMAC-SHA256(PASSWORD_LINK_HMAC_SECRET, tenant_id + exp + 현재 password 값(NULL이면 빈 문자열)))
 * 현재 비밀번호 해시가 서명에 들어가므로 비밀번호를 한 번 설정하면 같은 링크는 더 이상 맞지 않는다(1회용).
 * 규칙을 바꾸면 Console과 같이 바꾼다. 링크 · 서명 값은 로그 · 응답에 남기지 않는다.
 */
@Component
public class PasswordLinkVerifier {

	private final byte[] secret;

	public PasswordLinkVerifier(TetraProperties properties) {
		this.secret = properties.passwordLink().hmacSecret().getBytes(StandardCharsets.UTF_8);
	}

	public boolean matches(String tenantId, long exp, String currentPassword, String sig) {
		if (sig == null || sig.isBlank()) {
			return false;
		}
		byte[] expected = sign(tenantId, exp, currentPassword).getBytes(StandardCharsets.US_ASCII);
		// 시간 차로 서명이 몇 글자 맞았는지 드러나지 않게 고정 시간 비교
		return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
	}

	String sign(String tenantId, long exp, String currentPassword) {
		String message = tenantId + exp + (currentPassword == null ? "" : currentPassword);
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC 계산 실패");
		}
	}

}
