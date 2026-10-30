package com.tetra.portal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tetra.portal.notification.NotificationRules.Chip;
import com.tetra.portal.notification.NotificationService.Category;

/** UI-TP-13 알림센터 칩 · 이동 경로 규칙과 알림 문구의 이벤트 표기. */
class NotificationRulesTest {

	static String link(String category, Long eventId) {
		return NotificationRules.link(NotificationRules.chip(category, eventId), eventId);
	}

	@Test
	void 칩은_신청_이벤트_리포트_3개() {
		assertThat(Chip.values()).containsExactly(Chip.APPLICATION, Chip.EVENT, Chip.REPORT);
	}

	@Test
	void category별_칩() {
		assertThat(NotificationRules.chip("APPLICATION", 1L)).isEqualTo(Chip.APPLICATION);
		assertThat(NotificationRules.chip("APPLICATION", null)).isEqualTo(Chip.APPLICATION);
		assertThat(NotificationRules.chip("HOSTING", 1L)).isEqualTo(Chip.EVENT);
		assertThat(NotificationRules.chip("EVENT", 1L)).isEqualTo(Chip.EVENT);
		assertThat(NotificationRules.chip("REPORT", null)).isEqualTo(Chip.REPORT);
	}

	@Test
	void 메일은_이벤트가_있을_때만_이벤트_칩() {
		// 개인키 메일(이벤트 있음) · 비밀번호 설정 메일(이벤트 없음 — Console의 테넌트 칩)
		assertThat(NotificationRules.chip("MAIL", 1L)).isEqualTo(Chip.EVENT);
		assertThat(NotificationRules.chip("MAIL", null)).isNull();
	}

	@Test
	void 운영자_전용_값과_모르는_값은_칩_없음() {
		for (String category : new String[] { "ACCOUNT", "PROVISIONING", "DELAY", "SYSTEM" }) {
			assertThat(NotificationRules.chip(category, 1L)).as(category).isNull();
		}
		assertThat(NotificationRules.chip("SLACK", 1L)).isNull();
		assertThat(NotificationRules.chip("hosting", 1L)).isNull();
		assertThat(NotificationRules.chip("", 1L)).isNull();
		assertThat(NotificationRules.chip(null, 1L)).isNull();
	}

	@Test
	void category_9개는_Console과_같은_순서() {
		assertThat(Category.values()).extracting(Enum::name)
			.containsExactly("APPLICATION", "HOSTING", "EVENT", "ACCOUNT", "PROVISIONING", "MAIL", "DELAY", "SYSTEM",
					"REPORT");
	}

	@Test
	void 신청은_이벤트_상세_기본_탭() {
		assertThat(link("APPLICATION", 1043L)).isEqualTo("/events/1043");
	}

	@Test
	void 이벤트는_상세_결과_탭() {
		assertThat(link("HOSTING", 1043L)).isEqualTo("/events/1043?tab=result");
		assertThat(link("EVENT", 1043L)).isEqualTo("/events/1043?tab=result");
		assertThat(link("MAIL", 1043L)).isEqualTo("/events/1043?tab=result");
	}

	@Test
	void 리포트는_이벤트와_무관하게_리포트_화면() {
		assertThat(link("REPORT", null)).isEqualTo("/reports");
		assertThat(link("REPORT", 1043L)).isEqualTo("/reports");
	}

	@Test
	void 이벤트가_없거나_칩이_없으면_링크_없음() {
		assertThat(link("APPLICATION", null)).isNull();
		assertThat(link("HOSTING", null)).isNull();
		assertThat(link("EVENT", null)).isNull();
		assertThat(link("MAIL", null)).isNull();
		assertThat(link("DELAY", 1043L)).isNull();
		assertThat(link("SLACK", 1043L)).isNull();
		assertThat(NotificationRules.link(null, 1043L)).isNull();
	}

	@Test
	void 이벤트_표기는_id와_이름_이름이_없으면_id만() {
		assertThat(NotificationService.eventLabel(1043L, "봄 쿠폰")).isEqualTo("[1043:봄 쿠폰]");
		assertThat(NotificationService.eventLabel(1043L, null)).isEqualTo("[1043]");
	}

}
