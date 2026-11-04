package com.venuepass.service;

import com.venuepass.common.BusinessException;
import com.venuepass.mapper.PaymentMapper;
import com.venuepass.mapper.ReservationMapper;
import com.venuepass.mapper.VenueMapper;
import com.venuepass.model.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationMapper reservations;
    private final VenueMapper venues;
    private final PaymentMapper payments;
    private final HotReservationService hot;
    private final int paymentMinutes;

    public ReservationService(ReservationMapper reservations, VenueMapper venues, PaymentMapper payments,
            HotReservationService hot, @org.springframework.beans.factory.annotation.Value("${venuepass.payment-minutes}") int paymentMinutes) {
        this.reservations = reservations; this.venues = venues; this.payments = payments;
        this.hot = hot; this.paymentMinutes = paymentMinutes;
    }

    @Transactional
    public Object submit(ReservationInput input, String username) {
        if (input.sessionId() == null || input.participantName() == null || input.participantName().isBlank())
            throw new BusinessException("Invalid reservation");
        Session session = venues.session(input.sessionId());
        if (session == null || !"OPEN".equals(session.status) || !session.startsAt.isAfter(LocalDateTime.now()))
            throw new BusinessException("Session unavailable");
        if (session.hot) return hot.enqueue(input, username);
        return create(input.sessionId(), username, input.participantName());
    }

    @Transactional
    public Reservation create(long sessionId, String username, String participantName) {
        if (reservations.byUser(sessionId, username) != null) throw new BusinessException("One reservation per user and session");
        if (venues.reserve(sessionId) != 1) throw new BusinessException("Session sold out or closed");
        Session session = venues.session(sessionId);
        Reservation reservation = new Reservation();
        reservation.sessionId = sessionId; reservation.username = username; reservation.participantName = participantName;
        reservation.status = "PENDING_PAYMENT"; reservation.amount = session.price;
        reservation.code = UUID.randomUUID().toString(); reservation.createdAt = LocalDateTime.now();
        reservation.expiresAt = reservation.createdAt.plusMinutes(paymentMinutes);
        reservations.insert(reservation);
        reservations.event(reservation.id, null, reservation.status, username);
        return reservation;
    }

    @Transactional
    public Reservation createQueued(HotReservationService.Request request) {
        Reservation existing = reservations.byUser(request.sessionId(), request.username());
        if (existing != null) {
            if ("CANCELLED".equals(existing.status) || "REFUNDED".equals(existing.status) || "REFUND_PENDING".equals(existing.status))
                throw new BusinessException("One reservation per user and session");
            return existing;
        }
        return create(request.sessionId(), request.username(), request.participantName());
    }

    public List<Reservation> mine(String username) { return reservations.forUser(username); }

    public Reservation mine(long id, String username) {
        Reservation reservation = reservations.get(id);
        if (reservation == null || !username.equals(reservation.username)) throw new BusinessException("Reservation not found");
        return reservation;
    }

    @Transactional
    public void recordPayment(long id, String externalReference) {
        if (externalReference == null || externalReference.isBlank()) throw new BusinessException("Payment reference required");
        Reservation reservation = reservations.lock(id);
        if (reservation == null || !"PENDING_PAYMENT".equals(reservation.status) || !reservation.expiresAt.isAfter(LocalDateTime.now()))
            throw new BusinessException("Reservation is not payable");
        payments.insert(id, externalReference, reservation.amount);
        transition(reservation, "PAID", "operator");
    }

    @Transactional
    public void cancel(long id, String actor, boolean operator) {
        Reservation reservation = reservations.lock(id);
        if (reservation == null || (!operator && !actor.equals(reservation.username))) throw new BusinessException("Reservation not found");
        cancelLocked(reservation, actor);
    }

    private void cancelLocked(Reservation reservation, String actor) {
        String next;
        if ("PENDING_PAYMENT".equals(reservation.status)) next = "CANCELLED";
        else if ("PAID".equals(reservation.status)) {
            if (reservations.requestRefund(reservation.id) != 1) throw new BusinessException("Refund already requested");
            next = "REFUND_PENDING";
        } else throw new BusinessException("Reservation cannot be cancelled");
        transition(reservation, next, actor);
        if (venues.release(reservation.sessionId) != 1) throw new BusinessException("Inventory release failed");
        Session session = venues.session(reservation.sessionId);
        if (session.hot) {
            reservations.queueRelease(reservation.id, reservation.sessionId, reservation.username);
            afterCommit(() -> {
                try { hot.release(reservation.sessionId, reservation.username); }
                catch (RuntimeException exception) { /* The scheduled task retries the release record. */ }
            });
        }
    }

    @Transactional
    public void expire(long id) {
        Reservation reservation = reservations.lock(id);
        if (reservation == null || !"PENDING_PAYMENT".equals(reservation.status) || reservation.expiresAt.isAfter(LocalDateTime.now())) return;
        cancelLocked(reservation, "system:expiry");
    }

    @Transactional
    public void checkIn(String code, String device, String operator) {
        if (code == null || code.isBlank() || device == null || device.isBlank()) throw new BusinessException("Code and device required");
        Reservation byCode = reservations.byCode(code);
        if (byCode == null) throw new BusinessException("Code not found");
        Reservation reservation = reservations.lock(byCode.id);
        if (!"PAID".equals(reservation.status)) throw new BusinessException("Code already used or inactive");
        Session session = venues.session(reservation.sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (!"OPEN".equals(session.status) || now.isBefore(session.startsAt) || now.isAfter(session.endsAt))
            throw new BusinessException("Check-in is outside session time");
        transition(reservation, "CHECKED_IN", operator);
        reservations.verify(reservation.id, operator, device);
        if (venues.admit(reservation.sessionId) != 1) throw new BusinessException("Inventory update failed");
    }

    @Transactional
    public void cancelSession(long id) {
        Session session = venues.lockSession(id);
        if (session == null || venues.cancelSession(id) != 1) throw new BusinessException("Session not open");
        for (Long reservationId : reservations.activeForSession(id)) {
            cancelLocked(reservations.lock(reservationId), "operator:session-cancel");
        }
        if (session.hot) afterCommit(() -> hot.clear(id));
    }

    @Transactional
    public void confirmRefund(long id, String reference) {
        if (reference == null || reference.isBlank()) throw new BusinessException("Refund reference required");
        Reservation reservation = reservations.lock(id);
        if (reservation == null || !"REFUND_PENDING".equals(reservation.status) || reservations.finishRefund(id, reference) != 1)
            throw new BusinessException("Refund is not pending");
        transition(reservation, "REFUNDED", "operator");
    }

    public Report report(long sessionId) { return reservations.report(sessionId); }
    public List<Long> expiredIds() { return reservations.expired(LocalDateTime.now()); }

    public void processPendingReleases() {
        for (InventoryRelease release : reservations.pendingReleases()) {
            try {
                hot.release(release.sessionId, release.username);
                reservations.finishRelease(release.reservationId);
            } catch (RuntimeException exception) {
                // Leave the row for the next scheduled attempt.
            }
        }
    }

    private void transition(Reservation reservation, String next, String actor) {
        if (reservations.transition(reservation.id, reservation.status, next) != 1) throw new BusinessException("Reservation state changed");
        reservations.event(reservation.id, reservation.status, next, actor);
        reservation.status = next;
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
