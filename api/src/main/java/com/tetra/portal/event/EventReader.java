package com.tetra.portal.event;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.StatusType;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.tenant.Event;
import com.tetra.portal.domain.tenant.EventRepository;
import com.tetra.portal.domain.tenant.EventStatusLog;
import com.tetra.portal.domain.tenant.EventStatusLogRepository;
import com.tetra.portal.domain.tenant.CouponRepository;
import com.tetra.portal.domain.tenant.EventResultSummaryRepository;
import com.tetra.portal.event.EventResponses.Actor;
import com.tetra.portal.result.ResultReader;

import tools.jackson.databind.json.JsonMapper;

/**
 * 테넌트 스키마 읽기. TenantRouter.inTenant 안에서만 부른다(트랜잭션은 라우팅 뒤에 열린다).
 * tetra_platform.event_registry는 읽지 않는다 — 테넌트 화면은 자기 스키마의 event만(논리 ERD).
 */
@Component
class EventReader {

	private final EventRepository events;

	private final CouponRepository coupons;

	private final EventStatusLogRepository statusLogs;

	private final EventResultSummaryRepository summaries;

	private final JsonMapper jsonMapper;

	private final EventDomain domains;

	EventReader(EventRepository events, CouponRepository coupons, EventStatusLogRepository statusLogs,
			EventResultSummaryRepository summaries, JsonMapper jsonMapper, EventDomain domains) {
		this.events = events;
		this.coupons = coupons;
		this.statusLogs = statusLogs;
		this.summaries = summaries;
		this.jsonMapper = jsonMapper;
		this.domains = domains;
	}

	@Transactional(readOnly = true)
	public List<EventResponses.Summary> list() {
		return events.findAllByOrderByAppliedAtDescEventIdDesc()
			.stream()
			.map(e -> new EventResponses.Summary(e.getEventId(), e.getName(), e.getEventType(), e.getApplicationStatus(),
					e.getHostingStatus(), e.getEventStatus(), Kst.toOffset(e.getStartAt()), Kst.toOffset(e.getEndAt()),
					Kst.toOffset(e.getHostingStartAt()), Kst.toOffset(e.getHostingEndAt()),
					Kst.toOffset(e.getAppliedAt()), e.getTrafficPlanCode()))
			.toList();
	}

	@Transactional(readOnly = true)
	public EventResponses.Detail detail(Long eventId, Tenant tenant) {
		Event e = events.findById(eventId).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
		var couponItems = coupons.findByEventIdOrderByCouponIdAsc(eventId)
			.stream()
			.map(c -> new EventResponses.CouponItem(c.getCouponId(), c.getName(), c.getDescription()))
			.toList();
		var logs = statusLogs.findByEventIdOrderByChangedAtAscLogIdAsc(eventId)
			.stream()
			.map(l -> new EventResponses.LogItem(l.getStatusType(), l.getPrevStatus(), l.getNewStatus(),
					Kst.toOffset(l.getChangedAt()), l.getDetail(), actor(l)))
			.toList();
		return new EventResponses.Detail(e.getEventId(), e.getName(), e.getEventType(), Kst.toOffset(e.getStartAt()),
				Kst.toOffset(e.getEndAt()), Kst.toOffset(e.getHostingStartAt()), Kst.toOffset(e.getHostingEndAt()),
				e.getEndpointUrl(), e.getTrafficPlanCode(), e.getStockCount(), Kst.toOffset(e.getAppliedAt()),
				tenant.getContactName(),
				new EventResponses.Status(e.getApplicationStatus(), e.getHostingStatus(), e.getEventStatus(),
						e.getRejectReason(), ResultReader.state(e, summaries.findById(eventId).orElse(null))),
				couponItems, jsonMapper.readTree(e.getLayoutConfig()), domains.of(e), logs);
	}

	/** 신청 제출(NULL → APPLYING)만 테넌트, changed_by가 있으면 운영자, 나머지는 시스템. */
	static Actor actor(EventStatusLog log) {
		if (log.getChangedBy() != null) {
			return Actor.OPERATOR;
		}
		if (log.getStatusType() == StatusType.APPLICATION && log.getPrevStatus() == null
				&& ApplicationStatus.APPLYING.name().equals(log.getNewStatus())) {
			return Actor.TENANT;
		}
		return Actor.SYSTEM;
	}

}
