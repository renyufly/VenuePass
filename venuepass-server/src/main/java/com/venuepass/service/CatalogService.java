package com.venuepass.service;

import com.venuepass.common.BusinessException;
import com.venuepass.mapper.VenueMapper;
import com.venuepass.model.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CatalogService {
    private final VenueMapper mapper;
    private final HotReservationService hot;

    public CatalogService(VenueMapper mapper, HotReservationService hot) { this.mapper = mapper; this.hot = hot; }

    public Venue createVenue(VenueInput input) {
        if (input.name() == null || input.name().isBlank() || input.address() == null || input.address().isBlank() || input.capacity() < 1)
            throw new BusinessException("Invalid venue");
        Venue venue = new Venue();
        venue.name = input.name(); venue.address = input.address(); venue.capacity = input.capacity();
        mapper.insert(venue);
        return venue;
    }

    public Event createEvent(EventInput input) {
        if (input.venueId() == null || mapper.get(input.venueId()) == null || input.title() == null || input.title().isBlank())
            throw new BusinessException("Invalid event");
        Event event = new Event();
        event.venueId = input.venueId(); event.title = input.title(); event.description = input.description(); event.active = true;
        mapper.insertEvent(event);
        return event;
    }

    @Transactional
    public Session createSession(SessionInput input) {
        if (input.eventId() == null || input.startsAt() == null || input.endsAt() == null ||
                !input.endsAt().isAfter(input.startsAt()) || !input.startsAt().isAfter(LocalDateTime.now()) ||
                input.price() == null || input.price().compareTo(BigDecimal.ZERO) < 0 || input.capacity() < 1)
            throw new BusinessException("Invalid session");
        Event event = mapper.event(input.eventId());
        if (event == null || !event.active) throw new BusinessException("Event unavailable");
        Venue venue = mapper.get(event.venueId);
        if (input.capacity() > venue.capacity) throw new BusinessException("Session exceeds venue capacity");
        Session session = new Session();
        session.eventId = input.eventId(); session.startsAt = input.startsAt(); session.endsAt = input.endsAt();
        session.price = input.price(); session.totalCapacity = input.capacity(); session.available = input.capacity();
        session.hot = input.hot(); session.status = "OPEN";
        mapper.insertSession(session);
        return session;
    }

    public List<Venue> venues() { return mapper.list(); }
    public List<Event> events() { return mapper.events(); }
    public List<Session> sessions(long eventId) { return mapper.sessions(eventId); }

    public void preheat(long sessionId) {
        Session session = mapper.session(sessionId);
        if (session == null || !session.hot || !"OPEN".equals(session.status)) throw new BusinessException("Hot session unavailable");
        hot.preheat(sessionId, session.available);
    }
}
