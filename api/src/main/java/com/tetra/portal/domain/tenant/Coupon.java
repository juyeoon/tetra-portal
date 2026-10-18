package com.tetra.portal.domain.tenant;

import com.tetra.portal.tenancy.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 쿠폰 (최대 10종, 일괄 발급). 수량 열은 없다 — 재고는 이벤트 단위 event.stock_count 하나(논리 ERD).
 * 노출 순서는 coupon_id 순서(신청 때 입력한 순서대로 INSERT).
 */
@Entity
@Table(name = "coupon")
public class Coupon extends TenantScopedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "coupon_id", nullable = false, columnDefinition = "int unsigned")
	private Long couponId;

	@Column(name = "event_id", nullable = false, columnDefinition = "int unsigned")
	private Long eventId;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "description", length = 500, nullable = false)
	private String description;

	protected Coupon() {
	}

	public Long getCouponId() {
		return couponId;
	}

	public Long getEventId() {
		return eventId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

}
