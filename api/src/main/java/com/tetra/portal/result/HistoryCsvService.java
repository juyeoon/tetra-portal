package com.tetra.portal.result;

import org.springframework.stereotype.Service;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.external.AuditFileLinks;
import com.tetra.portal.tenancy.TenantRouter;

/** 발급 이력 CSV 다운로드 링크 — 결과가 READY일 때만. 링크는 AuditFileLinks(자기 감사 버킷만, presigned). */
@Service
public class HistoryCsvService {

	private final TenantRouter router;

	private final ResultReader reader;

	private final AuditFileLinks links;

	HistoryCsvService(TenantRouter router, ResultReader reader, AuditFileLinks links) {
		this.router = router;
		this.reader = reader;
		this.links = links;
	}

	public ResultResponse.CsvDownload link(String tenantId, Long eventId) {
		String path = router.inTenant(tenantId, () -> reader.historyFilePathIfReady(eventId));
		if (path == null || path.isBlank()) {
			throw new ApiException(ErrorCode.RESULT_NOT_READY);
		}
		var link = links.link(tenantId, path, "issuance-history-" + eventId + ".csv");
		return new ResultResponse.CsvDownload(link.url(), link.expiresAt());
	}

}
