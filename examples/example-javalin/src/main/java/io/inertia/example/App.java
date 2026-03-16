package io.inertia.example;

import io.inertia.core.ClasspathTemplateResolver;
import io.inertia.core.InertiaConfig;
import io.inertia.core.InertiaEngine;
import io.inertia.javalin.Inertia;
import io.inertia.javalin.InertiaPlugin;
import io.javalin.Javalin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class App {

    private static final Map<Long, Map<String, Object>> events = new ConcurrentHashMap<>();
    private static final AtomicLong idGen = new AtomicLong(1);

    public static void main(String[] args) {
        // Seed data
        addEvent("Javalin Meetup", "An evening of Javalin talks", "2026-04-15");
        addEvent("Java Conf 2026", "Annual Java conference", "2026-06-20");
        addEvent("Inertia.js Workshop", "Hands-on Inertia.js with Java", "2026-05-10");

        // Determine template based on DEV env
        boolean isDev = "true".equals(System.getenv("DEV"));
        String templatePath = isDev ? "templates/app-dev.html" : "templates/app.html";

        // Set up Inertia engine
        InertiaConfig config = InertiaConfig.builder()
                .version("1.0.0")
                .templateResolver(new ClasspathTemplateResolver(templatePath))
                .build();

        InertiaEngine engine = new InertiaEngine(config);
        engine.addSharedPropsResolver(req -> Map.of(
                "auth", Map.of("user", Map.of("name", "Demo User"))
        ));

        InertiaPlugin plugin = new InertiaPlugin(engine);
        Inertia inertia = plugin.inertia();

        // Create Javalin app with routes
        Javalin app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/static");
            plugin.configure(cfg);

            cfg.routes.get("/", ctx -> inertia.render(ctx, "Home"));

            cfg.routes.get("/events", ctx -> inertia.render(ctx, "Events/Index", Map.of(
                    "events", new ArrayList<>(events.values())
            )));

            cfg.routes.get("/events/create", ctx -> inertia.render(ctx, "Events/Create"));

            cfg.routes.get("/events/{id}", ctx -> {
                long id = Long.parseLong(ctx.pathParam("id"));
                Map<String, Object> event = events.get(id);
                if (event != null) {
                    inertia.render(ctx, "Events/Show", Map.of("event", event));
                } else {
                    ctx.status(404);
                }
            });

            cfg.routes.post("/events", ctx -> {
                @SuppressWarnings("unchecked")
                Map<String, String> body = ctx.bodyAsClass(Map.class);
                String title = body.get("title");
                String description = body.get("description");
                String date = body.get("date");

                Map<String, String> errors = new LinkedHashMap<>();
                if (title == null || title.isBlank()) errors.put("title", "Title is required");
                if (description == null || description.isBlank()) errors.put("description", "Description is required");
                if (date == null || date.isBlank()) errors.put("date", "Date is required");

                if (!errors.isEmpty()) {
                    inertia.redirectWithErrors(ctx, "/events/create", errors);
                    return;
                }

                addEvent(title, description, date);
                inertia.redirect(ctx, "/events");
            });

            cfg.routes.delete("/events/{id}", ctx -> {
                long id = Long.parseLong(ctx.pathParam("id"));
                events.remove(id);
                inertia.redirect(ctx, "/events");
            });
        });

        app.start(8080);
    }

    private static void addEvent(String title, String description, String date) {
        long id = idGen.getAndIncrement();
        events.put(id, Map.of(
                "id", id,
                "title", title,
                "description", description,
                "date", date
        ));
    }
}
