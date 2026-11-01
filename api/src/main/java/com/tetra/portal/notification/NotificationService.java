package com.tetra.portal.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.platform.EventRegistry;
import com.tetra.portal.domain.platform.EventRegistryRepository;
import com.tetra.portal.domain.platform.OperatorNotification;
import com.tetra.portal.domain.platform.OperatorNotificationRepository;
import com.tetra.portal.domain.platform.TenantRepository;
import com.tetra.portal.domain.tenant.TenantNotification;
import com.tetra.portal.domain.tenant.TenantNotificationRepository;
import com.tetra.portal.domain.tenant.TenantNotificationRepository.CountGroup;
import com.tetra.portal.notification.NotificationRules.Chip;
import com.tetra.portal.tenancy.TenantContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * UI-TP-13 알림센터와, Portal이 남기는 알림(신청 결과)의 유일한 곳. TenantRouter.inTenant 안에서만 부른다.
 * 남기는 규칙 · 문구는 Console(CLAUDE.md §7.13)과 같다 — 상태 변경 트랜잭션 안에서(MANDATORY), 한 사건에 한 번.
 * 운영자 알림은 [테넌트명] [id:이벤트명], 테넌트 알림은 [id:이벤트명]부터. 실패는 detail이 있으면 " 원인: {detail}".
 */
@Service
public class NotificationService {

	static final int CONTENT_MAX = 1000;

	/** Console NotificationService.Category와 같은 값 · 순서. Portal이 남기는 것은 APPLICATION뿐이고 나머지는 칩 판정에 쓴다 */
	enum Category {

		APPLICATION, HOSTING, EVENT, ACCOUNT, PROVISIONING, MAIL,
		/** 단계 지연(Console 1분 주기 확인, 운영자 알림만) */
		DELAY,
		/** 이벤트 하나에 속하지 않는 자동화 문제(Console 1분 주기 확인, 운영자 알림만) */
		SYSTEM,
		/** 리포트 생성(미구현) */
		REPORT

	}

	enum Priority {

		URGENT, WARNING, INFO

	}

	private final TenantNotificationRepository notifications;

	private final OperatorNotificationRepository operatorNotifications;

	private final TenantRepository tenants;

	private final EventRegistryRepository registries;

	private final Clock clock;

	NotificationService(TenantNotificationRepository notifications,
			OperatorNotificationRepository operatorNotifications, TenantRepository tenants,
			EventRegistryRepository registries, Clock clock) {
		this.notifications = notifications;
		this.operatorNotifications = operatorNotifications;
		this.tenants = tenants;
		this.registries = registries;
		this.clock = clock;
	}

	/** 화면 PortalNotification과 같은 이름. priority는 DB 값(URGENT · WARNING · INFO) — 화면 이름은 긴급 · 주의 · 정보. */
	public record NotificationItem(Long notificationId, @Schema(description = "관련 이벤트 — 없으면 null") Long eventId,
			@Schema(description = "분류 — DB 값 그대로(APPLICATION · HOSTING · EVENT · REPORT 등)") String category,
			@Schema(description = "URGENT · WARNING · INFO") String priority, OffsetDateTime occurredAt, String content,
			boolean read,
			@Schema(description = "칩. 신청 · 이벤트 · 리포트가 아니면 null(분류 '전체'에서만)") Chip chip,
			@Schema(description = "누르면 갈 화면 경로('/'로 시작). 없으면 null(읽음만)",
					example = "/events/1043?tab=result") String link) {
	}

	// ------------------------------------------------------------ 알림센터 목록 (UI-TP-13)

	/** 한 페이지 기본 크기 · 최대 크기 */
	static final int PAGE_SIZE = 10;

	static final int SIZE_MAX = 50;

	/** 페이지 상한 — OFFSET이 int를 넘으면 Spring Data가 500을 낸다(최대 크기로도 50만 건) */
	static final int PAGE_MAX = 10_000;

	/** 검색어 상한(앞뒤 공백을 지운 뒤) */
	static final int QUERY_MAX = 100;

	/** LIKE 이스케이프 문자 — MySQL 문자열에서 특수한 백슬래시 대신 */
	static final char LIKE_ESCAPE = '!';

	private static final String ALL = "ALL";

	/** 최신순, 같은 시각이면 나중 알림 먼저 */
	private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("notificationId"));

	/**
	 * 목록 조건. priority · chip이 null이면 전체, q는 내용 부분 일치(앞뒤 공백을 지우고 비면 null). page는 1부터.
	 * 범위 밖이면 400 — @RequestParam 검증 애너테이션은 처리하지 않는 예외(500)로 끝나서 여기서 직접 검사한다.
	 */
	public record ListCondition(Priority priority, Chip chip, boolean unreadOnly, String q, int page, int size) {

		public ListCondition {
			q = q == null || q.isBlank() ? null : q.strip();
			if (q != null && q.length() > QUERY_MAX) {
				throw ApiException.invalid("q", QUERY_MAX + "자 이하로 입력해 주세요.");
			}
			if (page < 1) {
				throw ApiException.invalid("page", "1 이상이어야 합니다.");
			}
			if (page > PAGE_MAX) {
				throw ApiException.invalid("page", PAGE_MAX + " 이하여야 합니다.");
			}
			if (size < 1) {
				throw ApiException.invalid("size", "1 이상이어야 합니다.");
			}
			if (size > SIZE_MAX) {
				throw ApiException.invalid("size", SIZE_MAX + " 이하여야 합니다.");
			}
		}

	}

	/** 목록 한 페이지 + 화면 건수. 건수 키는 화면 필터 키(ALL + 값)와 같다 */
	public record NotificationPage(List<NotificationItem> items, @Schema(description = "1부터") int page, int size,
			@Schema(description = "조건(검색 포함)에 맞는 전체 수") long totalCount,
			@Schema(description = "0이면 맞는 알림 없음. page가 이보다 크면 items가 비어 있다") int totalPages,
			@Schema(description = "안 읽은 알림 수(조건과 무관) — [모두 읽음] 버튼") long unreadCount, Counts counts) {
	}

	public record Counts(
			@Schema(description = "ALL · URGENT · WARNING · INFO — 안 읽은 것만만 따른다(분류 · 검색과 무관)",
					example = "{\"ALL\": 42, \"URGENT\": 3, \"WARNING\": 9, \"INFO\": 30}") Map<String, Long> priority,
			@Schema(description = "ALL · APPLICATION · EVENT · REPORT — 우선순위 · 안 읽은 것만을 따른다(고른 분류 · 검색과 무관). 칩이 없는 알림은 ALL에만",
					example = "{\"ALL\": 42, \"APPLICATION\": 12, \"EVENT\": 25, \"REPORT\": 2}") Map<String, Long> chip) {
	}

	/** 조건에 맞는 한 페이지(최신순)와 건수. 항목마다 칩 · 이동 경로를 붙인다({@link NotificationRules}) */
	@Transactional(readOnly = true)
	public NotificationPage list(ListCondition c) {
		Page<TenantNotification> found = notifications.findAll(where(c),
				PageRequest.of(c.page() - 1, c.size(), LATEST_FIRST));
		List<CountGroup> groups = notifications.countGroups();
		return new NotificationPage(found.getContent().stream().map(NotificationService::item).toList(), c.page(),
				c.size(), found.getTotalElements(), found.getTotalPages(), unreadCount(groups), counts(groups, c));
	}

	@Transactional
	public void markRead(String tenantId, Long notificationId) {
		if (notifications.markRead(notificationId, tenantId) == 0) {
			throw new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}
	}

	/** 그 테넌트의 안 읽은 알림 전부(현재 페이지만이 아님). 없어도 그대로 끝난다 */
	@Transactional
	public void markAllRead(String tenantId) {
		notifications.markAllRead(tenantId);
	}

	private static NotificationItem item(TenantNotification n) {
		Chip chip = NotificationRules.chip(n.getCategory(), n.getEventId());
		return new NotificationItem(n.getNotificationId(), n.getEventId(), n.getCategory(), n.getPriority(),
				Kst.toOffset(n.getOccurredAt()), n.getContent(), n.isRead(), chip,
				NotificationRules.link(chip, n.getEventId()));
	}

	/** 목록 조건 → WHERE. 값이 있는 조건만 붙인다 */
	private static Specification<TenantNotification> where(ListCondition c) {
		List<Specification<TenantNotification>> parts = new ArrayList<>();
		if (c.priority() != null) {
			parts.add((n, query, cb) -> cb.equal(n.get("priority"), c.priority().name()));
		}
		if (c.chip() != null) {
			parts.add(chipIs(NotificationRules.match(c.chip())));
		}
		if (c.unreadOnly()) {
			parts.add((n, query, cb) -> cb.isFalse(n.get("read")));
		}
		if (c.q() != null) {
			parts.add((n, query, cb) -> cb.like(n.get("content"), "%" + escapeLike(c.q()) + "%", LIKE_ESCAPE));
		}
		return Specification.allOf(parts);
	}

	/** 칩 조건 — category IN 묶음, 이벤트 유무가 갈리는 묶음은 event_id 조건과 함께. 빈 묶음은 붙이지 않는다 */
	private static Specification<TenantNotification> chipIs(NotificationRules.Match m) {
		return (n, query, cb) -> {
			Path<String> category = n.get("category");
			List<Predicate> any = new ArrayList<>();
			if (!m.any().isEmpty()) {
				any.add(category.in(m.any()));
			}
			if (!m.withEvent().isEmpty()) {
				any.add(cb.and(category.in(m.withEvent()), cb.isNotNull(n.get("eventId"))));
			}
			if (!m.withoutEvent().isEmpty()) {
				any.add(cb.and(category.in(m.withoutEvent()), cb.isNull(n.get("eventId"))));
			}
			return cb.or(any.toArray(Predicate[]::new));
		};
	}

	/** 검색어의 ! · % · _를 글자 그대로 찾게 한다 */
	static String escapeLike(String q) {
		return q.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	private static long unreadCount(List<CountGroup> groups) {
		return groups.stream().filter(g -> !g.getRead()).mapToLong(CountGroup::getTotal).sum();
	}

	/**
	 * 건수 — 우선순위는 안 읽은 것만만 따르고, 분류는 우선순위 · 안 읽은 것만을 따른다(고른 분류 · 검색과 무관).
	 * 칩은 목록 항목과 같은 NotificationRules.chip으로 정하고, 칩이 없는 알림은 ALL에만 센다.
	 */
	private static Counts counts(List<CountGroup> groups, ListCondition c) {
		Map<String, Long> priority = zeros(Priority.values());
		Map<String, Long> chip = zeros(Chip.values());
		for (CountGroup g : groups) {
			if (c.unreadOnly() && g.getRead()) {
				continue;
			}
			add(priority, ALL, g.getTotal());
			add(priority, g.getPriority(), g.getTotal());
			if (c.priority() != null && !c.priority().name().equals(g.getPriority())) {
				continue;
			}
			add(chip, ALL, g.getTotal());
			add(chip, NotificationRules.chip(g.getCategory(), true), g.getWithEvent());
			add(chip, NotificationRules.chip(g.getCategory(), false), g.getTotal() - g.getWithEvent());
		}
		return new Counts(priority, chip);
	}

	private static Map<String, Long> zeros(Enum<?>[] keys) {
		Map<String, Long> counts = new LinkedHashMap<>();
		counts.put(ALL, 0L);
		for (Enum<?> key : keys) {
			counts.put(key.name(), 0L);
		}
		return counts;
	}

	private static void add(Map<String, Long> counts, Chip chip, long n) {
		if (chip != null) {
			add(counts, chip.name(), n);
		}
	}

	/** 표에 없는 키(모르는 우선순위)는 세지 않는다 */
	private static void add(Map<String, Long> counts, String key, long n) {
		counts.computeIfPresent(key, (k, v) -> v + n);
	}

	// ------------------------------------------------------------ 신청 결과 (APPLICATION, WF1-1 b · c)

	/**
	 * APPLYING → APPLIED. 운영자 WARNING(승인 검토가 필요) · 테넌트 INFO.
	 * 계정 INACTIVE → ACTIVE는 같은 순간이라 따로 남기지 않는다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void applicationApplied(String tenantId, Long eventId) {
		String event = eventLabel(eventId);
		operator(tenantId, eventId, Category.APPLICATION, Priority.WARNING,
				tenantLabel(tenantId) + " " + event + " 이벤트 신청이 접수되었습니다. 승인 검토가 필요합니다.");
		tenant(tenantId, eventId, Category.APPLICATION, Priority.INFO, event + " 이벤트 신청이 접수되었습니다.");
	}

	/** APPLYING → APPLY_FAILED(배너 업로드 실패 · 시간 초과). 운영자 · 테넌트 WARNING. 테넌트는 다시 신청하면 된다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void applicationFailed(String tenantId, Long eventId, String detail) {
		String event = eventLabel(eventId);
		String cause = detail == null ? "" : " 원인: " + detail;
		operator(tenantId, eventId, Category.APPLICATION, Priority.WARNING,
				tenantLabel(tenantId) + " " + event + " 이벤트 신청 접수에 실패했습니다." + cause);
		tenant(tenantId, eventId, Category.APPLICATION, Priority.WARNING,
				event + " 이벤트 신청이 접수되지 않았습니다. 다시 신청해 주세요." + cause);
	}

	// ------------------------------------------------------------

	private void operator(String tenantId, Long eventId, Category category, Priority priority, String content) {
		operatorNotifications.save(OperatorNotification.unread(tenantId, eventId, category.name(), priority.name(),
				Kst.now(clock), fit(content)));
	}

	/** tenant_notification은 테넌트 스키마 테이블이라 라우팅된 테넌트와 같아야 한다(이중 확인은 엔티티 리스너가 한다). */
	private void tenant(String tenantId, Long eventId, Category category, Priority priority, String content) {
		if (!tenantId.equals(TenantContext.current())) {
			throw new IllegalStateException("테넌트 알림은 해당 테넌트 스키마 라우팅 안에서만 남길 수 있습니다.");
		}
		notifications.save(TenantNotification.unread(tenantId, eventId, category.name(), priority.name(),
				Kst.now(clock), fit(content)));
	}

	private String tenantLabel(String tenantId) {
		return tenants.findById(tenantId).map(t -> "[" + t.getName() + "]").orElse("[" + tenantId + "]");
	}

	private String eventLabel(Long eventId) {
		return eventLabel(eventId, registries.findById(eventId).map(EventRegistry::getName).orElse(null));
	}

	/** [id:이벤트명]. 이름을 모르면 [id] (Console NotificationService.eventLabel과 같다) */
	static String eventLabel(Long eventId, String eventName) {
		return eventName == null ? "[" + eventId + "]" : "[" + eventId + ":" + eventName + "]";
	}

	static String fit(String content) {
		return content.length() <= CONTENT_MAX ? content : content.substring(0, CONTENT_MAX);
	}

}
