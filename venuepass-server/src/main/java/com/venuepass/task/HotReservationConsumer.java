package com.venuepass.task;

import com.venuepass.common.BusinessException;
import com.venuepass.model.Models.Reservation;
import com.venuepass.service.HotReservationService;
import com.venuepass.service.ReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HotReservationConsumer {
    private final HotReservationService hot;
    private final ReservationService reservations;
    public HotReservationConsumer(HotReservationService hot, ReservationService reservations) {
        this.hot = hot; this.reservations = reservations;
    }

    @KafkaListener(topics = HotReservationService.TOPIC)
    public void consume(String payload) {
        HotReservationService.Request request = hot.decode(payload);
        try {
            Reservation reservation = reservations.createQueued(request);
            hot.result(request.sessionId(), request.username(), "CONFIRMED:" + reservation.id);
        } catch (BusinessException exception) {
            hot.release(request.sessionId(), request.username());
            hot.result(request.sessionId(), request.username(), "FAILED:" + exception.getMessage());
        }
    }
}
