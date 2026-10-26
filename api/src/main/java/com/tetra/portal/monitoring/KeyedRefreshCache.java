package com.tetra.portal.monitoring;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 키(예: eventId · 범위)마다 {@link RefreshCache} 하나. 일정 시간 안 쓰인 키는 다음 조회 때 지운다
 * (화면을 닫은 이벤트의 캐시가 쌓이지 않게). 키에 사용자 입력을 넣을 때는 검증한 값만 넣는다.
 */
final class KeyedRefreshCache<K, T> {

	static final Duration IDLE_EVICT = Duration.ofMinutes(2);

	private final Function<K, RefreshCache<T>> factory;

	private final Map<K, RefreshCache<T>> caches = new ConcurrentHashMap<>();

	KeyedRefreshCache(Function<K, RefreshCache<T>> factory) {
		this.factory = factory;
	}

	T get(K key) {
		evictIdle();
		return caches.computeIfAbsent(key, factory).get();
	}

	int size() {
		return caches.size();
	}

	private void evictIdle() {
		long now = System.nanoTime();
		caches.entrySet().removeIf(e -> now - e.getValue().lastUsedNanos() > IDLE_EVICT.toNanos());
	}

}
