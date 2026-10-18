package com.tetra.portal.tenancy;

/** 라우팅한 테넌트와 다른 tenant_id의 행을 만났을 때. 응답은 500. */
public class TenantMismatchException extends RuntimeException {

	public TenantMismatchException() {
		super("라우팅한 테넌트와 행의 tenant_id가 다릅니다.");
	}

}
