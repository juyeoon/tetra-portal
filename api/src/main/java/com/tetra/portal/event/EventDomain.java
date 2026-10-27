package com.tetra.portal.event;

import org.springframework.stereotype.Component;

import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.tenant.Event;

/**
 * 이벤트 주소 {slug}.{tenant_id}.{플랫폼 도메인}. 상세(UI-TP-06) · 모니터링(UI-TP-11)이 같이 쓰고, 사전 가이드의 세션 요청 주소도 이 값이다.
 * 플랫폼 도메인은 EVENT_ROOT_DOMAIN(기본 tetraops.cloud). 테넌트 root_domain(고객사 원래 도메인, 등록 때 받는 값)은 쓰지 않는다.
 */
@Component
public class EventDomain {

	private final String rootDomain;

	EventDomain(TetraProperties properties) {
		this.rootDomain = properties.event().rootDomain();
	}

	/** Console EventReader와 같은 조건: 승인됐고 만료 전일 때만 도메인을 보여 준다. */
	public String of(Event e) {
		boolean visible = e.getApplicationStatus() == ApplicationStatus.APPROVED
				&& e.getEventStatus() != EventStatus.EXPIRED && e.getSlug() != null;
		return visible ? e.getSlug() + "." + e.getTenantId() + "." + rootDomain : null;
	}

}
