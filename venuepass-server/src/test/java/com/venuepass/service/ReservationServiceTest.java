package com.venuepass.service;

import com.venuepass.common.BusinessException;
import com.venuepass.mapper.PaymentMapper;
import com.venuepass.mapper.ReservationMapper;
import com.venuepass.mapper.VenueMapper;
import com.venuepass.model.Models.Reservation;
import com.venuepass.model.Models.InventoryRelease;
import com.venuepass.model.Models.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ReservationMapper reservations;
    private VenueMapper venues;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationMapper.class);
        venues = mock(VenueMapper.class);
        service = new ReservationService(reservations, venues, mock(PaymentMapper.class), mock(HotReservationService.class), 15);
    }

    @Test
    void soldOutDoesNotCreateReservation() {
        when(venues.reserve(7)).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.create(7, "alice", "Alice"));
        verify(reservations, never()).insert(any());
    }

    @Test
    void createSnapshotsPriceAndExpiry() {
        when(venues.reserve(7)).thenReturn(1);
        Session session = new Session(); session.price = new BigDecimal("12.50");
        when(venues.session(7)).thenReturn(session);
        doAnswer(invocation -> { Reservation value = invocation.getArgument(0); value.id = 42L; return null; })
            .when(reservations).insert(any());
        Reservation created = service.create(7, "alice", "Alice");
        assertEquals("PENDING_PAYMENT", created.status);
        assertEquals(new BigDecimal("12.50"), created.amount);
        assertEquals(created.createdAt.plusMinutes(15), created.expiresAt);
        verify(reservations).event(42L, null, "PENDING_PAYMENT", "alice");
    }

    @Test
    void expiryReleasesOnce() {
        Reservation reservation = new Reservation();
        reservation.id = 42L; reservation.sessionId = 7L; reservation.username = "alice";
        reservation.status = "PENDING_PAYMENT"; reservation.expiresAt = LocalDateTime.now().minusMinutes(1);
        when(reservations.lock(42)).thenReturn(reservation);
        when(reservations.transition(42, "PENDING_PAYMENT", "CANCELLED")).thenReturn(1);
        when(venues.release(7)).thenReturn(1);
        when(venues.session(7)).thenReturn(new Session());
        service.expire(42);
        service.expire(42);
        verify(venues, times(1)).release(7);
        verify(reservations).event(42, "PENDING_PAYMENT", "CANCELLED", "system:expiry");
    }

    @Test
    void checkInUsesCodeOnlyOnce() {
        Reservation reservation = new Reservation();
        reservation.id = 42L; reservation.sessionId = 7L; reservation.code = "ticket"; reservation.status = "PAID";
        when(reservations.byCode("ticket")).thenReturn(reservation);
        when(reservations.lock(42)).thenReturn(reservation);
        Session session = new Session(); session.status = "OPEN";
        session.startsAt = LocalDateTime.now().minusHours(1); session.endsAt = LocalDateTime.now().plusHours(1);
        when(venues.session(7)).thenReturn(session);
        when(reservations.transition(42, "PAID", "CHECKED_IN")).thenReturn(1);
        when(venues.admit(7)).thenReturn(1);
        service.checkIn("ticket", "gate-1", "checker");
        assertThrows(BusinessException.class, () -> service.checkIn("ticket", "gate-1", "checker"));
        verify(reservations, times(1)).verify(42, "checker", "gate-1");
        verify(venues, times(1)).admit(7);
    }

    @Test
    void paidCancellationRequestsRefundAndReleasesInventory() {
        Reservation reservation = new Reservation();
        reservation.id = 42L; reservation.sessionId = 7L; reservation.username = "alice"; reservation.status = "PAID";
        when(reservations.lock(42)).thenReturn(reservation);
        when(reservations.requestRefund(42)).thenReturn(1);
        when(reservations.transition(42, "PAID", "REFUND_PENDING")).thenReturn(1);
        when(venues.release(7)).thenReturn(1);
        when(venues.session(7)).thenReturn(new Session());
        service.cancel(42, "alice", false);
        assertEquals("REFUND_PENDING", reservation.status);
        verify(reservations).requestRefund(42);
        verify(venues).release(7);
    }

    @Test
    void redisReleaseIsRetriedFromDatabaseRecord() {
        HotReservationService hot = mock(HotReservationService.class);
        ReservationService retrying = new ReservationService(reservations, venues, mock(PaymentMapper.class), hot, 15);
        InventoryRelease release = new InventoryRelease();
        release.reservationId = 42L; release.sessionId = 7L; release.username = "alice";
        when(reservations.pendingReleases()).thenReturn(List.of(release));
        doThrow(new RuntimeException("Redis down")).doNothing().when(hot).release(7, "alice");
        retrying.processPendingReleases();
        verify(reservations, never()).finishRelease(42);
        retrying.processPendingReleases();
        verify(reservations).finishRelease(42);
    }
}
