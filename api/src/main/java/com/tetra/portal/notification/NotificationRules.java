package com.tetra.portal.notification;

import java.util.ArrayList;
import java.util.List;

import com.tetra.portal.notification.NotificationService.Category;

/**
 * UI-TP-13 알림센터 항목의 칩 · 이동 경로(목록 API가 계산하고 화면은 그대로 쓴다). 순수 규칙만.
 * 칩 이름 · 순서는 Console NotificationRules.Chip과 같고, Portal은 그중 신청 · 이벤트 · 리포트만 쓴다.
 * - 칩: 신청 = APPLICATION, 이벤트 = HOSTING · EVENT · MAIL(event_id 있음), 리포트 = REPORT.
 *   그 밖(모르는 값 포함)은 null(분류 "전체"에서만 보임). 목록 필터 · 건수도 이 규칙 하나로 판정한다({@link #match}).
 * - 이동 경로: 신청 = 이벤트 상세(기본 탭 신청 내용), 이벤트 = 상세 결과 탭, 리포트 = 리포트 화면.
 *   리포트 말고는 event_id가 없으면 null(읽음만).
 */
public final class NotificationRules {

	private NotificationRules() {
	}

	/** 알림센터 칩 */
	public enum Chip {

		APPLICATION, EVENT, REPORT

	}

	static Chip chip(String category, Long eventId) {
		return chip(category, eventId != null);
	}

	/** 칩은 category와 event_id 유무만 본다 */
	static Chip chip(String category, boolean hasEvent) {
		Category known = category(category);
		if (known == null) {
			return null;
		}
		return switch (known) {
			case APPLICATION -> Chip.APPLICATION;
			case HOSTING, EVENT -> Chip.EVENT;
			// 개인키 메일(이벤트 있음)만. 이벤트가 없는 메일(비밀번호 설정)은 Console의 테넌트 칩이라 Portal에는 없다
			case MAIL -> hasEvent ? Chip.EVENT : null;
			case REPORT -> Chip.REPORT;
			// 운영자 알림에만 쓰는 값
			case ACCOUNT, PROVISIONING, DELAY, SYSTEM -> null;
		};
	}

	/**
	 * 칩 하나에 드는 category 묶음 — 목록 필터의 SQL 조건이 쓴다.
	 * any = 이벤트와 상관없이, withEvent = event_id가 있을 때만, withoutEvent = event_id가 없을 때만.
	 */
	record Match(List<String> any, List<String> withEvent, List<String> withoutEvent) {
	}

	/** {@link #chip(String, boolean)}을 모든 Category × 이벤트 있음/없음에 돌려 나눈다(규칙을 SQL에 따로 쓰지 않는다) */
	static Match match(Chip chip) {
		List<String> any = new ArrayList<>();
		List<String> withEvent = new ArrayList<>();
		List<String> withoutEvent = new ArrayList<>();
		for (Category c : Category.values()) {
			boolean with = chip(c.name(), true) == chip;
			boolean without = chip(c.name(), false) == chip;
			if (with && without) {
				any.add(c.name());
			}
			else if (with) {
				withEvent.add(c.name());
			}
			else if (without) {
				withoutEvent.add(c.name());
			}
		}
		return new Match(List.copyOf(any), List.copyOf(withEvent), List.copyOf(withoutEvent));
	}

	/** 화면 경로(같은 앱 안, '/'로 시작). 필요한 event_id가 없으면 null */
	static String link(Chip chip, Long eventId) {
		if (chip == null) {
			return null;
		}
		return switch (chip) {
			case APPLICATION -> eventId == null ? null : "/events/" + eventId;
			// 결과 탭이 막혀 있으면(진행 전 · 중) 상세 화면이 신청 내용 탭으로 대신 연다(EventDetailPage)
			case EVENT -> eventId == null ? null : "/events/" + eventId + "?tab=result";
			case REPORT -> "/reports";
		};
	}

	private static Category category(String value) {
		for (Category c : Category.values()) {
			if (c.name().equals(value)) {
				return c;
			}
		}
		return null;
	}

}
