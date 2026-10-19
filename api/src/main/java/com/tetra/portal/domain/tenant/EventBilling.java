package com.tetra.portal.domain.tenant;

import java.math.BigDecimal;

import com.tetra.portal.tenancy.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 이벤트 비용(1:1). 신청 때 적용 단가 · 예상 비용을 넣는다(P3). actual_cost 등은 1차에서 쓰지 않는다. */
@Entity
@Table(name = "event_billing")
public class EventBilling extends TenantScopedEntity {

	@Id
	@Column(name = "event_id", nullable = false, columnDefinition = "int unsigned")
	private Long eventId;

	@Column(name = "applied_hourly_price", precision = 15, scale = 2, nullable = false)
	private BigDecimal appliedHourlyPrice;

	@Column(name = "applied_hosting_hourly_price", precision = 15, scale = 2, nullable = false)
	private BigDecimal appliedHostingHourlyPrice;

	@Column(name = "estimated_cost", precision = 15, scale = 2, nullable = false)
	private BigDecimal estimatedCost;

	@Column(name = "actual_cost", precision = 15, scale = 2)
	private BigDecimal actualCost;

	@Column(name = "budget_alert_threshold", precision = 15, scale = 2)
	private BigDecimal budgetAlertThreshold;

	@Column(name = "extracharge_amount", precision = 15, scale = 2)
	private BigDecimal extrachargeAmount;

	protected EventBilling() {
	}

	/** 신청 때 단가를 고정해 둔다(요금제 단가가 나중에 바뀌어도 이 이벤트는 신청 때 값). */
	public static EventBilling applied(Long eventId, String tenantId, BigDecimal hourlyPrice,
			BigDecimal hostingHourlyPrice, BigDecimal estimatedCost) {
		EventBilling b = new EventBilling();
		b.eventId = eventId;
		b.tenantId = tenantId;
		b.appliedHourlyPrice = hourlyPrice;
		b.appliedHostingHourlyPrice = hostingHourlyPrice;
		b.estimatedCost = estimatedCost;
		return b;
	}

	public Long getEventId() {
		return eventId;
	}

	public BigDecimal getAppliedHourlyPrice() {
		return appliedHourlyPrice;
	}

	public BigDecimal getAppliedHostingHourlyPrice() {
		return appliedHostingHourlyPrice;
	}

	public BigDecimal getEstimatedCost() {
		return estimatedCost;
	}

}
