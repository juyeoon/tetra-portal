package com.tetra.portal.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.tetra.portal.config.TetraProperties;

/**
 * 테넌트 관리자 JWT 발급·검증 (계획서 4-1). Console 운영자 토큰과 같은 구조, 키 · 이름 · 경로만 다르다.
 * HS256(PORTAL_JWT_SECRET) · sub = tenant_id · 8시간 · HttpOnly · Secure · SameSite=Strict · Path=/api/portal 쿠키.
 * 서버 세션을 쓰지 않으므로 로그아웃은 쿠키만 지운다(토큰 자체는 만료 전까지 유효).
 * 토큰 값은 로그·예외 메시지에 넣지 않는다.
 */
@Component
public class JwtTokenService {

	public static final String COOKIE_NAME = "TETRA_PORTAL_TOKEN";

	static final String COOKIE_PATH = "/api/portal";

	static final Duration TTL = Duration.ofHours(8);

	static final String ISSUER = "tetra-portal-api";

	private final JwtEncoder encoder;

	private final JwtDecoder decoder;

	private final Clock clock;

	public JwtTokenService(TetraProperties properties, Clock clock) {
		SecretKey key = new SecretKeySpec(properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// 다른 서비스가 같은 키로 만든 토큰을 받지 않도록 발급자도 확인한다
		JwtTimestampValidator timestamp = new JwtTimestampValidator();
		timestamp.setClock(clock);
		nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(ISSUER)));
		this.decoder = nimbus;
		this.clock = clock;
	}

	public JwtDecoder decoder() {
		return decoder;
	}

	public String issue(String tenantId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(tenantId)
			.issuedAt(now)
			.expiresAt(now.plus(TTL))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public ResponseCookie loginCookie(String token) {
		return baseCookie(token).maxAge(TTL).build();
	}

	public ResponseCookie logoutCookie() {
		return baseCookie("").maxAge(Duration.ZERO).build();
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
		return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(true).sameSite("Strict").path(COOKIE_PATH);
	}

}
