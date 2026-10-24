package com.tetra.portal.domain.platform;

import org.springframework.data.jpa.repository.JpaRepository;

/** tetra_platform.operator_notification — 넣기만 한다(NotificationService). */
public interface OperatorNotificationRepository extends JpaRepository<OperatorNotification, Long> {

}
