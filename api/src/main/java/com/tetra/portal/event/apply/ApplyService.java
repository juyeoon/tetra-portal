package com.tetra.portal.event.apply;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.EventType;
import com.tetra.portal.domain.platform.Template;
import com.tetra.portal.domain.platform.TemplateRepository;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.platform.TrafficPlan;
import com.tetra.portal.domain.platform.TrafficPlanRepository;
import com.tetra.portal.external.BannerImage;
import com.tetra.portal.tenancy.TenantRouter;

import tools.jackson.databind.json.JsonMapper;

/**
 * WF1-1 이벤트 신청 (계획서 4-3).
 * ① 검사(실패 → 400 fields) ② 트랜잭션 a(registry · event APPLYING …) ③ 202 ④ 커밋 뒤 배너 비동기 업로드 → b / c.
 * 검사는 DB CHECK와 같은 규칙을 먼저 확인해 화면에 항목별 사유를 돌려준다(DB 오류 500이 나지 않게).
 */
@Service
public class ApplyService {

	private final TenantRouter router;

	private final TrafficPlanRepository plans;

	private final TemplateRepository templates;

	private final ApplyWriter writer;

	private final BannerUploader uploader;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	ApplyService(TenantRouter router, TrafficPlanRepository plans, TemplateRepository templates, ApplyWriter writer,
			BannerUploader uploader, JsonMapper jsonMapper, Clock clock) {
		this.router = router;
		this.plans = plans;
		this.templates = templates;
		this.writer = writer;
		this.uploader = uploader;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/** 접수한 event_id. 이 시점의 상태는 APPLYING — 배너 업로드 결과로 APPLIED / APPLY_FAILED가 된다. */
	public Long apply(String tenantId, ApplyRequest request, MultipartFile banner) {
		Tenant tenant = router.findTenant(tenantId);
		Map<String, String> errors = new LinkedHashMap<>();

		BannerImage image = readBanner(banner, errors);
		LocalDateTime startAt = kst(request.startAt());
		LocalDateTime endAt = kst(request.endAt());
		LocalDateTime hostingStartAt = kst(request.hostingStartAt());
		LocalDateTime hostingEndAt = kst(request.hostingEndAt());
		if (!endAt.isAfter(startAt)) {
			errors.putIfAbsent("endAt", "이벤트 종료는 시작보다 뒤여야 합니다.");
		}
		if (!hostingEndAt.isAfter(hostingStartAt)) {
			errors.putIfAbsent("hostingEndAt", "호스팅 종료는 시작보다 뒤여야 합니다.");
		}
		if (hostingStartAt.isAfter(startAt) || hostingEndAt.isBefore(endAt)) {
			errors.putIfAbsent("hostingStartAt", "호스팅 기간이 이벤트 기간을 포함해야 합니다.");
		}
		if (!hostingStartAt.isAfter(Kst.now(clock))) {
			errors.putIfAbsent("hostingStartAt", "호스팅 시작은 지금보다 뒤여야 합니다.");
		}

		TrafficPlan plan = plans.findById(request.trafficPlanCode()).filter(TrafficPlan::isVisible).orElse(null);
		if (plan == null) {
			errors.put("trafficPlanCode", "선택할 수 없는 요금제입니다.");
		}
		Template template = templates.findByEventType(request.eventType()).orElse(null);
		if (template == null) {
			errors.put("eventType", "아직 신청할 수 없는 이벤트 유형입니다.");
		}
		if (request.eventType() == EventType.COUPON && request.coupons().isEmpty()) {
			errors.put("coupons", "쿠폰을 1개 이상 입력해 주세요.");
		}
		if (request.eventType() != EventType.COUPON && !request.coupons().isEmpty()) {
			errors.put("coupons", "쿠폰은 쿠폰 이벤트에서만 입력합니다.");
		}
		if (!isHttpUrl(request.endpointUrl())) {
			errors.put("endpointUrl", "http(s)로 시작하는 주소를 입력해 주세요.");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_INPUT, errors);
		}

		BigDecimal estimated = estimatedCost(hostingStartAt, hostingEndAt, plan);
		var application = new ApplyWriter.Application(tenantId, request.name().strip(), request.eventType(),
				template.getTemplateId(), jsonMapper.writeValueAsString(request.layout()), startAt, endAt,
				hostingStartAt, hostingEndAt, request.endpointUrl().strip(), plan.getPlanCode(), request.stockCount(),
				request.coupons(), plan.getHourlyPrice(), plan.getHostingHourlyPrice(), estimated);
		Long eventId = router.inTenant(tenant, () -> writer.create(application));
		uploader.submit(tenantId, eventId, image); // 커밋 뒤
		return eventId;
	}

	/** 예상 비용 = ceil(호스팅 시간) × (요금제 시간당 + 호스팅 시간당). 화면이 보낸 값은 쓰지 않는다. */
	static BigDecimal estimatedCost(LocalDateTime hostingStartAt, LocalDateTime hostingEndAt, TrafficPlan plan) {
		long minutes = Duration.between(hostingStartAt, hostingEndAt).toMinutes();
		long hours = (minutes + 59) / 60;
		return plan.getHourlyPrice().add(plan.getHostingHourlyPrice()).multiply(BigDecimal.valueOf(hours));
	}

	private static BannerImage readBanner(MultipartFile banner, Map<String, String> errors) {
		if (banner == null || banner.isEmpty()) {
			errors.put("banner", "배너 이미지를 올려 주세요.");
			return null;
		}
		if (banner.getSize() > BannerImage.MAX_BYTES) {
			errors.put("banner", "2MB 이하 파일만 올릴 수 있습니다.");
			return null;
		}
		try {
			BannerImage image = BannerImage.detect(banner.getBytes());
			if (image == null) {
				errors.put("banner", "PNG 또는 JPEG 파일만 올릴 수 있습니다.");
			}
			return image;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static LocalDateTime kst(OffsetDateTime value) {
		return value.atZoneSameInstant(Kst.ZONE).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
	}

	/** 형식만 본다(접속 확인은 하지 않음 — 계획서 4-3). */
	static boolean isHttpUrl(String value) {
		try {
			URI uri = new URI(value.strip());
			String scheme = uri.getScheme();
			String host = uri.getHost();
			return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
					&& host != null && host.contains(".") && !host.startsWith(".") && !host.endsWith(".");
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

}
