package com.venuepass.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Models {
    private Models() { }

    public static class Venue {
        public Long id;
        public String name;
        public String address;
        public int capacity;
    }

    public static class Event {
        public Long id;
        public Long venueId;
        public String title;
        public String description;
        public boolean active;
    }

    public static class Session {
        public Long id;
        public Long eventId;
        public LocalDateTime startsAt;
        public LocalDateTime endsAt;
        public BigDecimal price;
        public int totalCapacity;
        public int available;
        public int locked;
        public boolean hot;
        public String status;
    }

    public static class Reservation {
        public Long id;
        public Long sessionId;
        public String username;
        public String participantName;
        public String status;
        public BigDecimal amount;
        public String code;
        public LocalDateTime expiresAt;
        public LocalDateTime createdAt;
    }

    public static class Report {
        public Long sessionId;
        public long reservations;
        public long paid;
        public long checkedIn;
        public BigDecimal revenue;
    }

    public static class InventoryRelease {
        public Long reservationId;
        public Long sessionId;
        public String username;
    }

    public record VenueInput(String name, String address, int capacity) { }
    public record EventInput(Long venueId, String title, String description) { }
    public record SessionInput(Long eventId, LocalDateTime startsAt, LocalDateTime endsAt,
                               BigDecimal price, int capacity, boolean hot) { }
    public record ReservationInput(Long sessionId, String participantName) { }
    public record CheckInInput(String code, String device) { }
}
