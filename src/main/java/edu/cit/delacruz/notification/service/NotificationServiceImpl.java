package edu.cit.delacruz.notification.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.notification.model.Notification;
import edu.cit.delacruz.notification.repository.NotificationRepository;

@Service
class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public Notification record(String type, String message) {
        Notification notification = new Notification(type, message, LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> getAll() {
        return notificationRepository.findAll(Sort.by(Sort.Direction.DESC, "notificationId"));
    }
}
