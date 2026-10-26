package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 모든 PromQL에 tenant_id · event_id를 함께 건다. 게이지 max, 카운터 sum. 입력 검증. */
class MonitoringQueriesTest {

	static final MonitoringQueries Q = new MonitoringQueries("aaaaaaaaaaaa", 7);

	/** 지표 이름 바로 뒤 셀렉터 — 모두 이 두 라벨로 시작해야 한다 */
	static final Pattern METRIC = Pattern
		.compile("(tetra_[a-z_]+|http_server_requests_seconds_(?:count|bucket)|redis_up)\\{([^\\n]*)");

	static List<String> all() {
		return List.of(Q.stockRemaining(), Q.waiting(), Q.sessionsActive(), Q.redisUp(), Q.soldOutSince(Duration.ofHours(2)),
				Q.processedLastMinute(), Q.successRate5m(), Q.rejects5m(), Q.rps(), Q.errorRps(), Q.claimP95(),
				Q.entriesPerMinute(), Q.claimsPerMinute());
	}

	@Test
	void 모든_식의_모든_지표에_tenant_id와_event_id가_함께_걸린다() {
		for (String q : all()) {
			Matcher m = METRIC.matcher(q);
			int metrics = 0;
			while (m.find()) {
				metrics++;
				assertThat(m.group(2)).as(q).startsWith("tenant_id=\"aaaaaaaaaaaa\",event_id=\"7\"");
			}
			assertThat(metrics).as(q).isPositive();
		}
		// 대기 인원은 두 게이지의 차 — 둘 다
		assertThat(Q.waiting().split("tenant_id=\"aaaaaaaaaaaa\",event_id=\"7\"", -1)).hasSize(3);
	}

	@Test
	void 게이지는_max_카운터는_sum() {
		assertThat(List.of(Q.stockRemaining(), Q.sessionsActive(), Q.redisUp())).allSatisfy(q -> assertThat(q).startsWith("max("));
		assertThat(Q.waiting()).matches("max\\(tetra_event_ticket_last\\{.*}\\) - max\\(tetra_event_cursor\\{.*}\\)");
		assertThat(List.of(Q.soldOutSince(Duration.ofHours(1)), Q.processedLastMinute(), Q.successRate5m(), Q.rejects5m(),
				Q.rps(), Q.errorRps(), Q.entriesPerMinute(), Q.claimsPerMinute()))
			.allSatisfy(q -> assertThat(q).startsWith("sum"));
		assertThat(Q.claimP95()).startsWith("histogram_quantile(0.95, sum by (le) (");
	}

	@Test
	void 발급_결과는_처음_결과만_replay_false() {
		assertThat(List.of(Q.soldOutSince(Duration.ofHours(1)), Q.processedLastMinute(), Q.successRate5m(), Q.claimsPerMinute()))
			.allSatisfy(q -> assertThat(q).contains("replay=\"false\""));
	}

	@Test
	void p95는_발급_uri_하나만() {
		assertThat(Q.claimP95()).contains("uri=\"/api/issuance/events/{eventId}/coupons/claim\"");
	}

	@Test
	void 품절_응답_누적_범위는_60초에서_7일_사이로_자른다() {
		assertThat(Q.soldOutSince(Duration.ofSeconds(10))).contains("[60s]");
		assertThat(Q.soldOutSince(Duration.ofMinutes(75))).contains("[4500s]");
		assertThat(Q.soldOutSince(Duration.ofDays(9))).contains("[604800s]");
	}

	@Test
	void 테넌트_형식이_틀리거나_이벤트가_양수가_아니면_만들지_않는다() {
		assertThatThrownBy(() -> new MonitoringQueries("aaaaaaaaaaa\"}", 7)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MonitoringQueries(null, 7)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MonitoringQueries("aaaaaaaaaaaa", 0)).isInstanceOf(IllegalArgumentException.class);
	}

}
