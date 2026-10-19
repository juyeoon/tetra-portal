package com.tetra.portal.report;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.tenant.TenantReportMeta;
import com.tetra.portal.domain.tenant.TenantReportMetaRepository;
import com.tetra.portal.external.AuditFileLinks;
import com.tetra.portal.external.MailGateway;
import com.tetra.portal.tenancy.TenantRouter;

/**
 * UI-TP-12 리포트 — 목록 · 다운로드 링크 · 메일 보내기. 파일은 테넌트 감사 버킷(Portal은 만들지 않는다).
 * TODO(확정 필요, 계획서 Q6): 리포트를 만드는 주체 · 형식. 메일은 지금 파일 첨부 없이 "Portal에서 내려받으세요" 안내만(Q13).
 */
@Service
public class ReportService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private final TenantRouter router;

	private final TenantReportMetaRepository reports;

	private final AuditFileLinks links;

	private final MailGateway mail;

	private final TetraProperties properties;

	ReportService(TenantRouter router, TenantReportMetaRepository reports, AuditFileLinks links, MailGateway mail,
			TetraProperties properties) {
		this.router = router;
		this.reports = reports;
		this.links = links;
		this.mail = mail;
		this.properties = properties;
	}

	/** 화면 Report와 같은 이름 + 기간은 시작 · 끝으로 나눔. */
	public record ReportItem(Long reportId, String name, String category, OffsetDateTime periodStart,
			OffsetDateTime periodEnd, OffsetDateTime createdAt) {

		static ReportItem of(TenantReportMeta r) {
			return new ReportItem(r.getReportId(), r.getName(), r.getCategory(), Kst.toOffset(r.getPeriodStart()),
					Kst.toOffset(r.getPeriodEnd()), Kst.toOffset(r.getCreatedAt()));
		}

	}

	public List<ReportItem> list(String tenantId) {
		return router.inTenant(tenantId, () -> readAll());
	}

	public AuditFileLinks.Link download(String tenantId, Long reportId) {
		TenantReportMeta report = router.inTenant(tenantId, () -> readOne(reportId));
		return links.link(tenantId, report.getFilePath(), "report-" + reportId + extension(report.getFilePath()));
	}

	/** 로그인한 계정 이메일로 안내 메일. 이메일 알림 설정(email_notification_enabled)과 상관없이 — 사용자가 직접 누른 것. */
	public void email(String tenantId, Long reportId) {
		Tenant tenant = router.findTenant(tenantId);
		TenantReportMeta report = router.inTenant(tenant, () -> readOne(reportId));
		String body = """
				%s 님, 요청하신 리포트를 안내드립니다.

				리포트: %s
				기간: %s ~ %s

				Tetra Portal의 리포트 화면에서 내려받으실 수 있습니다.
				%s/reports
				""".formatted(tenant.getContactName(), report.getName(), DATE.format(report.getPeriodStart()),
				DATE.format(report.getPeriodEnd()), properties.publicUrl());
		if (!mail.send(new MailGateway.Mail(tenant.getEmail(), null, "[Tetra] 리포트 — " + report.getName(), body))) {
			throw new ApiException(ErrorCode.MAIL_SEND_FAILED);
		}
	}

	/** 저장소 메서드 자체가 트랜잭션이라 여기서 따로 열지 않는다(같은 클래스 안 호출이라 @Transactional도 걸리지 않음). */
	List<ReportItem> readAll() {
		return reports.findAllByOrderByCreatedAtDescReportIdDesc().stream().map(ReportItem::of).toList();
	}

	TenantReportMeta readOne(Long reportId) {
		return reports.findById(reportId).orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
	}

	/** 저장 파일 이름의 확장자(영문 · 숫자만, 없으면 빈 문자열). */
	static String extension(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		int dot = name.lastIndexOf('.');
		if (dot < 0) {
			return "";
		}
		String ext = name.substring(dot + 1);
		return ext.matches("[A-Za-z0-9]{1,10}") ? "." + ext.toLowerCase() : "";
	}

}
