package io.github.emmajiugo.inertia.example.controller;

import io.github.emmajiugo.inertia.example.model.Event;
import io.github.emmajiugo.inertia.example.service.EventService;
import io.github.emmajiugo.inertia.spring.Inertia;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class EventController {

    private final Inertia inertia;
    private final EventService eventService;

    public EventController(Inertia inertia, EventService eventService) {
        this.inertia = inertia;
        this.eventService = eventService;
    }

    @GetMapping("/")
    public void home(HttpServletRequest req, HttpServletResponse res) throws IOException {
        inertia.render(req, res, "Home");
    }

    @GetMapping("/events")
    public void index(HttpServletRequest req, HttpServletResponse res) throws IOException {
        inertia.render(req, res, "Events/Index", Map.of(
                "events", eventService.findAll()
        ));
    }

    @GetMapping("/events/{id}")
    public void show(@PathVariable Long id,
                     HttpServletRequest req, HttpServletResponse res) throws IOException {
        eventService.findById(id).ifPresentOrElse(
                event -> {
                    try {
                        inertia.render(req, res, "Events/Show", Map.of("event", event));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> res.setStatus(404)
        );
    }

    @GetMapping("/events/create")
    public void create(HttpServletRequest req, HttpServletResponse res) throws IOException {
        inertia.render(req, res, "Events/Create");
    }

    @PostMapping("/events")
    public void store(@RequestBody Map<String, String> body,
                      HttpServletRequest req, HttpServletResponse res) {
        String title = body.get("title");
        String description = body.get("description");
        String date = body.get("date");

        Map<String, String> errors = new LinkedHashMap<>();
        if (title == null || title.isBlank()) {
            errors.put("title", "Title is required");
        }
        if (description == null || description.isBlank()) {
            errors.put("description", "Description is required");
        }
        if (date == null || date.isBlank()) {
            errors.put("date", "Date is required");
        }

        if (!errors.isEmpty()) {
            inertia.redirectWithErrors(req, res, "/events/create", errors);
            return;
        }

        eventService.create(new Event(null, title, description, LocalDate.parse(date)));
        inertia.redirect(res, "/events");
    }

    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable Long id, HttpServletResponse res) {
        eventService.delete(id);
        inertia.redirect(res, "/events");
    }
}