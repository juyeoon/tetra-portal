package com.tetra.portal.domain.platform;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficPlanRepository extends JpaRepository<TrafficPlan, String> {

	/** 신청 화면에 보여 줄 요금제 — 작은 것부터. */
	List<TrafficPlan> findByVisibleTrueOrderByMaxCapacityAsc();

}
