package com.tetra.portal.common.audit;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tetra.portal.common.time.Kst;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 감사 로그 바깥 필터 — 보안 체인보다 앞(AuditWebConfig), /api/portal/** 만(헬스 체크 · 화면 파일 제외).
 * 요청 시작에 AuditContext · request_id(MDC)를 만들고, 끝날 때 허용 목록(AuditActions)이면 한 줄을 낸다.
 * 보안 체인에서 401 · 403으로 끝난 요청도 여기서 남는다(행위자 없음).
 * 판정: 상태 < 400 → SUCCESS, 그 밖 → FAILURE + 오류 응답 code. 예외가 이 필터까지 나오면 FAILURE + UNHANDLED.
 */
public class AuditFilter extends OncePerRequestFilter {

	static final String PREFIX = "/api/portal/";

	static final String MDC_KEY = "request_id";

	static final String UNHANDLED = "UNHANDLED";

	static final int USER_AGENT_MAX = 512;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
		.withZone(Kst.ZONE);

	private static final Pattern EVENT_ID = Pattern.compile("^[0-9]{1,18}$");

	private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

	private final Clock clock;

	public AuditFilter(Clock clock) {
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().substring(request.getContextPath().length()).startsWith(PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Kst.now()는 초 단위라 쓰지 않는다 — 감사 시각은 ms까지
		Instant startedAt = clock.instant();
		String requestId = RequestIds.resolve(request.getHeader(RequestIds.HEADER));
		String ip = ClientIp.resolve(request);
		String userAgent = AuditContext.limit(request.getHeader(HttpHeaders.USER_AGENT), USER_AGENT_MAX);
		AuditContext context = AuditContext.start(request);
		MDC.put(MDC_KEY, requestId);
		boolean completed = false;
		try {
			chain.doFilter(request, response);
			completed = true;
		}
		finally {
			try {
				write(request, response, context, completed, startedAt, requestId, ip, userAgent);
			}
			catch (RuntimeException ex) {
				// 감사 줄 때문에 응답을 망치지 않는다. 값은 넣지 않는다(무엇이 들어 있을지 모름)
				log.warn("감사 줄을 만들지 못했습니다 ({})", ex.getClass().getSimpleName());
			}
			finally {
				MDC.remove(MDC_KEY);
			}
		}
	}

	private void write(HttpServletRequest request, HttpServletResponse response, AuditContext context,
			boolean completed, Instant startedAt, String requestId, String ip, String userAgent) {
		AuditActions.Match match = AuditActions.match(request).orElse(null);
		if (match == null) {
			return;
		}
		AuditActions.Entry entry = match.entry();
		boolean success = completed && response.getStatus() < 400;
		String pathId = match.pathVariable();

		// 대상: 행위별 보강 값 > 표의 idFrom(경로 변수 · 로그인한 테넌트). 로그인 · 비밀번호 링크는 보강 값만 —
		// 다른 테넌트 쿠키를 든 채 실패한 시도에 그 쿠키 테넌트가 대상으로 찍히지 않게
		AuditEvent.Party target = context.target();
		if (target == null && entry.targetType() != null) {
			String id = AuditActions.PRINCIPAL.equals(entry.idFrom()) ? context.tenantId() : pathId;
			if (id != null) {
				target = new AuditEvent.Party(entry.targetType(), AuditContext.limit(id, AuditContext.ID_MAX));
			}
		}
		Long eventId = context.eventId();
		if (eventId == null && AuditContext.EVENT.equals(entry.targetType()) && pathId != null
				&& EVENT_ID.matcher(pathId).matches()) {
			eventId = Long.valueOf(pathId);
		}

		AuditLogger.write(new AuditEvent(AuditEvent.LOG_TYPE, UUID.randomUUID().toString(), TIME.format(startedAt),
				AuditEvent.APP, context.actor(), ip, userAgent, entry.action(), target, context.tenantId(), eventId,
				success ? AuditEvent.SUCCESS : AuditEvent.FAILURE,
				success ? null : (completed ? context.reason() : UNHANDLED),
				// 바뀐 값 · 시도한 계정은 그 결과일 때만 의미가 있다
				success ? context.changes() : null, null, success ? null : context.attempt(), requestId));
	}

}
