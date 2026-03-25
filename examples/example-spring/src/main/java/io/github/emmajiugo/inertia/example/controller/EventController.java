package io.github.emmajiugo.inertia.example.controller;

import io.github.emmajiugo.inertia.example.model.Event;
import io.github.emmajiugo.inertia.example.service.EventService;
import io.github.emmajiugo.inertia.spring.Inertia;
import io.github.emmajiugo.javalidator.Validator;
import io.github.emmajiugo.javalidator.model.ValidationResponse;
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

        ValidationResponse validationResponse = Validator.validateMap(body, Map.of(
                "title",    "required",
                "description", "required",
                "date", "required"
        ));

        if (!validationResponse.valid()) {
            inertia.redirectWithErrors(req, res, "/events/create", validationResponse.toFlatMap());
            return;
        }

        eventService.create(new Event(
                null, body.get("title"), body.get("description"),
                LocalDate.parse(body.get("date"))
        ));
        inertia.redirect(res, "/events");
    }

    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable Long id, HttpServletResponse res) {
        eventService.delete(id);
        inertia.redirect(res, "/events");
    }
}