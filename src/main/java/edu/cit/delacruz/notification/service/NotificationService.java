package edu.cit.delacruz.notification.service;

import java.util.List;

import edu.cit.delacruz.notification.model.Notification;

/**
 * The Notification module's own public contract, same package-private-impl
 * pattern used by Inventory. Nothing else in this codebase calls into it
 * in-process — it's driven by domain events and read via its own REST
 * endpoint — but the same enforced boundary is kept for consistency.
 */
public interface NotificationService {

    Notification record(String type, String message);

    List<Notification> getAll();
}
