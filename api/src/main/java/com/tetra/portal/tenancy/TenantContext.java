package com.tetra.portal.tenancy;

import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 현재 스레드가 다룰 테넌트. 값이 없으면 공용 스키마(tetra_platform)를 쓴다.
 * 직접 set 하지 않고 {@link TenantRouter}가 READY 확인을 마친 뒤 {@link #callAs}로만 지정한다.
 * 비동기로 넘길 때는 {@link #current()}를 함께 넘기고 받은 쪽에서 다시 callAs로 감싼다.
 */
public final class TenantContext {

	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	/** 라우팅된 tenant_id. 공용 스키마면 null. */
	public static String current() {
		return CURRENT.get();
	}

	static <T> T callAs(String tenantId, Supplier<T> work) {
		TenantIds.requireValid(tenantId);
		String previous = CURRENT.get();
		if (tenantId.equals(previous)) {
			return work.get();
		}
		if (previous != null) {
			throw new IllegalStateException("다른 테넌트 컨텍스트 안에서 테넌트를 바꿀 수 없습니다.");
		}
		// 이미 열린 트랜잭션(=이미 정해진 커넥션)이 있으면 스키마를 바꿀 수 없다. 섞이지 않도록 막는다.
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("트랜잭션 안에서는 테넌트를 바꿀 수 없습니다.");
		}
		CURRENT.set(tenantId);
		try {
			return work.get();
		}
		finally {
			CURRENT.remove();
		}
	}

}
