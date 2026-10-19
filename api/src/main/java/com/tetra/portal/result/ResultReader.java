package com.tetra.portal.result;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;
import com.tetra.portal.domain.StatusType;
import com.tetra.portal.domain.platform.TrafficPlanRepository;
import com.tetra.portal.domain.tenant.Event;
import com.tetra.portal.domain.tenant.EventRepository;
import com.tetra.portal.domain.tenant.EventResultSummary;
import com.tetra.portal.domain.tenant.EventResultSummaryRepository;
import com.tetra.portal.domain.tenant.EventStatusLog;
import com.tetra.portal.domain.tenant.EventStatusLogRepository;
import com.tetra.portal.result.ResultResponse.Composition;
import com.tetra.portal.result.ResultResponse.Failure;
import com.tetra.portal.result.ResultResponse.Metrics;
import com.tetra.portal.result.ResultResponse.MinuteBucket;
import com.tetra.portal.result.ResultResponse.State;
import com.tetra.portal.tenancy.TenantContext;
import com.tetra.portal.tenancy.TenantMismatchException;

import jakarta.persistence.EntityManager;

/**
 * 결과 읽기 — Console EventReader의 결과 부분과 같은 계산(두 화면 숫자가 같아야 한다). TenantRouter.inTenant 안에서만.
 */
@Component
public class ResultReader {

	private static final Logger log = LoggerFactory.getLogger(ResultReader.class);

	private final EventRepository events;

	private final EventResultSummaryRepository summaries;

	private final EventStatusLogRepository statusLogs;

	private final TrafficPlanRepository plans;

	private final EntityManager entityManager;

	ResultReader(EventRepository events, EventResultSummaryRepository summaries, EventStatusLogRepository statusLogs,
			TrafficPlanRepository plans, EntityManager entityManager) {
		this.events = events;
		this.summaries = summaries;
		this.statusLogs = statusLogs;
		this.plans = plans;
		this.entityManager = entityManager;
	}

	/** 판정. 위에서부터 먼저 맞는 것. 결과 API와 상세 API(resultAvailable)가 같이 쓴다. */
	public static State state(Event event, EventResultSummary summary) {
		if (event.getHostingStatus() == HostingStatus.END_FAILED) {
			return State.END_FAILED;
		}
		if (summary != null && summary.getHistoryGeneratedAt() != null) {
			return State.READY;
		}
		if (event.getEventStatus() == EventStatus.EXPIRED) {
			return State.AGGREGATING;
		}
		return State.IN_PROGRESS;
	}

	@Transactional(readOnly = true)
	public State state(Long eventId) {
		return state(requireEvent(eventId), summaries.findById(eventId).orElse(null));
	}

	@Transactional(readOnly = true)
	public ResultResponse result(Long eventId) {
		Event event = requireEvent(eventId);
		EventResultSummary summary = summaries.findById(eventId).orElse(null);
		var openAt = Kst.toOffset(event.getStartAt());
		return switch (state(event, summary)) {
			case END_FAILED -> new ResultResponse(eventId, State.END_FAILED, openAt, null, event.getHostingStatus(),
					event.getEventStatus(), null, endFailure(eventId), null);
			case READY -> new ResultResponse(eventId, State.READY, openAt, Kst.toOffset(summary.getHistoryGeneratedAt()),
					event.getHostingStatus(), event.getEventStatus(), null, null, readyResult(event, summary));
			case AGGREGATING -> new ResultResponse(eventId, State.AGGREGATING, openAt, null, event.getHostingStatus(),
					event.getEventStatus(), null, null, null);
			case IN_PROGRESS -> new ResultResponse(eventId, State.IN_PROGRESS, openAt, null, event.getHostingStatus(),
					event.getEventStatus(), Kst.toOffset(event.getEndAt()), null, null);
		};
	}

	/** CSV 경로. READY가 아니거나 경로가 없으면 null. */
	@Transactional(readOnly = true)
	public String historyFilePathIfReady(Long eventId) {
		Event event = requireEvent(eventId);
		EventResultSummary summary = summaries.findById(eventId).orElse(null);
		if (state(event, summary) != State.READY) {
			return null;
		}
		return summary.getHistoryFilePath();
	}

	private Failure endFailure(Long eventId) {
		return statusLogs.findByEventIdOrderByChangedAtAscLogIdAsc(eventId)
			.stream()
			.filter(l -> l.getStatusType() == StatusType.HOSTING && HostingStatus.END_FAILED.name().equals(l.getNewStatus()))
			.reduce((first, second) -> second) // 가장 최근
			.map(l -> new Failure(l.getDetail(), Kst.toOffset(l.getChangedAt())))
			.orElse(new Failure(null, null));
	}

	private ResultResponse.Result readyResult(Event event, EventResultSummary summary) {
		Long totalStock = event.getStockCount();
		Long maxCapacity = plans.findById(event.getTrafficPlanCode())
			.orElseThrow(() -> new IllegalStateException("플랜이 없습니다: " + event.getTrafficPlanCode()))
			.getMaxCapacity();
		BigDecimal usage = maxCapacity == null || maxCapacity == 0 ? null
				: BigDecimal.valueOf(summary.getPeakConcurrent())
					.divide(BigDecimal.valueOf(maxCapacity), 4, RoundingMode.HALF_UP);
		long success = nz(summary.getSuccessCount());
		long soldout = nz(summary.getSoldoutCount());
		long abandoned = Math.max(0, nz(summary.getTicketCount()) - success - soldout);
		return new ResultResponse.Result(
				new Metrics(summary.getSuccessCount(), totalStock, summary.getSelloutRate(), summary.getSoldoutCount(),
						summary.getPeakConcurrent(), maxCapacity, usage, Kst.toOffset(summary.getPeakAt())),
				new Composition(success, soldout, abandoned), perMinute(event.getEventId()));
	}

	/**
	 * issuance_history를 queue_entered_at 분 단위로 묶는다. event_id로 uk_issuance_event_user 범위만 읽는다.
	 * 엔티티를 거치지 않으므로 다른 tenant_id 행이 섞였는지 여기서 직접 확인한다(이중 확인).
	 */
	@SuppressWarnings("unchecked")
	private List<MinuteBucket> perMinute(Long eventId) {
		String tenantId = TenantContext.current();
		List<Object[]> rows = entityManager.createNativeQuery("""
				select date_format(queue_entered_at, '%Y-%m-%d %H:%i:00') as minute_at,
				       sum(result = 'SUCCESS') as success_count,
				       sum(result = 'FAILED_SOLDOUT') as soldout_count,
				       sum(tenant_id <> :tenantId) as foreign_rows
				  from issuance_history
				 where event_id = :eventId
				 group by minute_at
				 order by minute_at""")
			.setParameter("tenantId", tenantId)
			.setParameter("eventId", eventId)
			.getResultList();
		List<MinuteBucket> buckets = new ArrayList<>(rows.size());
		for (Object[] row : rows) {
			if (toLong(row[3]) > 0) {
				log.error("테넌트 불일치: issuance_history eventId={}, routedTenant={}", eventId, tenantId);
				throw new TenantMismatchException();
			}
			LocalDateTime minute = LocalDateTime.parse(row[0].toString().replace(' ', 'T'));
			buckets.add(new MinuteBucket(Kst.toOffset(minute), toLong(row[1]), toLong(row[2])));
		}
		return buckets;
	}

	private Event requireEvent(Long eventId) {
		return events.findById(eventId).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
	}

	private static long nz(Long value) {
		return value == null ? 0 : value;
	}

	private static long toLong(Object value) {
		return value == null ? 0 : ((Number) value).longValue();
	}

}
