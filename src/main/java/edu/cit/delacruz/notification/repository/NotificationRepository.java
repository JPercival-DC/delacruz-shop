package edu.cit.delacruz.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.cit.delacruz.notification.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
