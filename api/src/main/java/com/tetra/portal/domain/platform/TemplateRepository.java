package com.tetra.portal.domain.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tetra.portal.domain.EventType;

public interface TemplateRepository extends JpaRepository<Template, Long> {

	Optional<Template> findByEventType(EventType eventType);

}
