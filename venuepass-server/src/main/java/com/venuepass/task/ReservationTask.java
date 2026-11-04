package com.venuepass.task;

import com.venuepass.service.ReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Adapted from the source project's timed order expiration task.
@Component
public class ReservationTask {
    private final ReservationService reservations;
    public ReservationTask(ReservationService reservations) { this.reservations = reservations; }

    @Scheduled(fixedDelay = 60_000)
    public void expire() {
        for (Long id : reservations.expiredIds()) {
            try { reservations.expire(id); }
            catch (RuntimeException exception) { /* Retry on the next scan. */ }
        }
        reservations.processPendingReleases();
    }
}
