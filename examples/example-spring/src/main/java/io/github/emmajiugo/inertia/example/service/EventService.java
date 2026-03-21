package io.github.emmajiugo.inertia.example.service;

import io.github.emmajiugo.inertia.example.model.Event;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EventService {

    private final Map<Long, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public EventService() {
        create(new Event(null, "Spring Boot Meetup", "An evening of Spring Boot talks", LocalDate.of(2026, 4, 15)));
        create(new Event(null, "Java Conf 2026", "Annual Java conference", LocalDate.of(2026, 6, 20)));
        create(new Event(null, "Inertia.js Workshop", "Hands-on Inertia.js with Java", LocalDate.of(2026, 5, 10)));
    }

    public List<Event> findAll() {
        return new ArrayList<>(events.values());
    }

    public Optional<Event> findById(Long id) {
        return Optional.ofNullable(events.get(id));
    }

    public Event create(Event event) {
        event.setId(idGenerator.getAndIncrement());
        events.put(event.getId(), event);
        return event;
    }

    public void update(Long id, Event event) {
        event.setId(id);
        events.put(id, event);
    }

    public void delete(Long id) {
        events.remove(id);
    }
}
