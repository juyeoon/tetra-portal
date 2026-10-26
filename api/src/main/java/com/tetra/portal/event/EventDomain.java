package com.tetra.portal.event;

import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.tenant.Event;

/** 이벤트 주소. 상세(UI-TP-06)와 모니터링(UI-TP-11)이 같이 쓴다. */
public final class EventDomain {

	private EventDomain() {
	}

	/** Console EventReader와 같은 규칙: 승인됐고 만료 전일 때만 도메인을 보여 준다. */
	public static String of(Event e, Tenant tenant) {
		boolean visible = e.getApplicationStatus() == ApplicationStatus.APPROVED
				&& e.getEventStatus() != EventStatus.EXPIRED && e.getSlug() != null;
		return visible ? e.getSlug() + "." + e.getTenantId() + "." + tenant.getRootDomain() : null;
	}

}
