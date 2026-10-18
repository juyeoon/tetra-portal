package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테넌트 스키마의 coupon. TenantRouter.inTenant 안에서만 쓴다. */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

	/** 노출 순서 = coupon_id 순서. */
	List<Coupon> findByEventIdOrderByCouponIdAsc(Long eventId);

}
