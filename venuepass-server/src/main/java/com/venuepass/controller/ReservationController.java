package com.venuepass.controller;

import com.venuepass.common.BusinessException;
import com.venuepass.common.Result;
import com.venuepass.mapper.AccountMapper;
import com.venuepass.model.Models.*;
import com.venuepass.service.HotReservationService;
import com.venuepass.service.ReservationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReservationController {
    public record Registration(String username, String password) { }
    public record PaymentRecord(String reference) { }

    private final ReservationService reservations;
    private final HotReservationService hot;
    private final AccountMapper accounts;
    private final PasswordEncoder encoder;

    public ReservationController(ReservationService reservations, HotReservationService hot, AccountMapper accounts, PasswordEncoder encoder) {
        this.reservations = reservations; this.hot = hot; this.accounts = accounts; this.encoder = encoder;
    }

    @PostMapping("/accounts") public Result<String> register(@RequestBody Registration input) {
        if (input.username() == null || !input.username().matches("[A-Za-z0-9_]{3,40}") ||
            "operator".equals(input.username()) || "checker".equals(input.username()) ||
            input.password() == null || input.password().length() < 8)
            throw new BusinessException("Invalid account");
        accounts.insert(input.username(), encoder.encode(input.password()));
        return Result.success(input.username());
    }

    @PostMapping("/reservations") public Result<Object> submit(@RequestBody ReservationInput input, Principal principal) {
        return Result.success(reservations.submit(input, principal.getName()));
    }
    @GetMapping("/reservations") public Result<List<Reservation>> mine(Principal principal) { return Result.success(reservations.mine(principal.getName())); }
    @GetMapping("/reservations/{id}") public Result<Reservation> one(@PathVariable long id, Principal principal) { return Result.success(reservations.mine(id, principal.getName())); }
    @PostMapping("/reservations/{id}/cancel") public Result<String> cancel(@PathVariable long id, Principal principal) {
        reservations.cancel(id, principal.getName(), false);
        return Result.success(reservations.mine(id, principal.getName()).status);
    }
    @GetMapping("/reservations/queue/{sessionId}") public Result<String> queue(@PathVariable long sessionId, Principal principal) {
        return Result.success(hot.result(sessionId, principal.getName()));
    }

    @PostMapping("/admin/reservations/{id}/payment") public Result<String> payment(@PathVariable long id, @RequestBody PaymentRecord input) {
        reservations.recordPayment(id, input.reference()); return Result.success("PAID");
    }
    @PostMapping("/admin/reservations/{id}/refund") public Result<String> refund(@PathVariable long id, @RequestBody PaymentRecord input) {
        reservations.confirmRefund(id, input.reference()); return Result.success("REFUNDED");
    }
    @PostMapping("/admin/sessions/{id}/cancel") public Result<String> cancelSession(@PathVariable long id) {
        reservations.cancelSession(id); return Result.success("CANCELLED");
    }
    @GetMapping("/admin/sessions/{id}/report") public Result<Report> report(@PathVariable long id) { return Result.success(reservations.report(id)); }
    @PostMapping("/check-in") public Result<String> checkIn(@RequestBody CheckInInput input, Principal principal) {
        reservations.checkIn(input.code(), input.device(), principal.getName()); return Result.success("CHECKED_IN");
    }
}
