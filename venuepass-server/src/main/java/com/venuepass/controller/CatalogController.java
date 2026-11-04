package com.venuepass.controller;

import com.venuepass.common.Result;
import com.venuepass.model.Models.*;
import com.venuepass.service.CatalogService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/venues") public Result<List<Venue>> venues() { return Result.success(catalog.venues()); }
    @GetMapping("/events") public Result<List<Event>> events() { return Result.success(catalog.events()); }
    @GetMapping("/events/{id}/sessions") public Result<List<Session>> sessions(@PathVariable long id) { return Result.success(catalog.sessions(id)); }
    @PostMapping("/admin/venues") public Result<Venue> venue(@RequestBody VenueInput input) { return Result.success(catalog.createVenue(input)); }
    @PostMapping("/admin/events") public Result<Event> event(@RequestBody EventInput input) { return Result.success(catalog.createEvent(input)); }
    @PostMapping("/admin/sessions") public Result<Session> session(@RequestBody SessionInput input) { return Result.success(catalog.createSession(input)); }
    @PostMapping("/admin/sessions/{id}/preheat") public Result<String> preheat(@PathVariable long id) { catalog.preheat(id); return Result.success("READY"); }
}
