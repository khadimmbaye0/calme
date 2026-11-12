package com.calme.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.calme.journal.EventCreateInverse;
import com.calme.journal.EventDeleteInverse;
import com.calme.journal.EventUpdateInverse;
import com.calme.journal.JournalRecorder;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private final EventRepository events;
    private final CurrentUserService currentUser;
    private final JournalRecorder journal;

    public CalendarService(
            EventRepository events,
            CurrentUserService currentUser,
            JournalRecorder journal) {
        this.events = events;
        this.currentUser = currentUser;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(Instant from, Instant to) {
        validateRange(from, to);
        AppUser user = currentUser.requireCurrentUser();
        return events
                .findByUserIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(user.getId(), to, from)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(long id) {
        return EventResponse.from(requireEvent(id));
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        AppUser user = currentUser.requireCurrentUser();
        String title = normalizedTitle(request.title());
        validateRange(request.startAt(), request.endAt());
        requireKind(request.kind());
        ensureNoConflict(user, request.startAt(), request.endAt(), null, request.kind());

        Event event = events.save(new Event(
                user,
                title,
                request.startAt(),
                request.endAt(),
                request.kind(),
                EventCreator.USER));
        journal.record(
                "event.create",
                Map.of("eventId", event.getId()),
                new EventCreateInverse(event.getId()),
                "Événement créé : « " + event.getTitle() + " ».");
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse update(long id, EventRequest request) {
        Event event = requireEvent(id);
        String title = normalizedTitle(request.title());
        validateRange(request.startAt(), request.endAt());
        requireKind(request.kind());
        ensureNoConflict(event.getUser(), request.startAt(), request.endAt(), event.getId(), request.kind());

        String previousTitle = event.getTitle();
        Instant previousStart = event.getStartAt();
        Instant previousEnd = event.getEndAt();
        EventKind previousKind = event.getKind();

        event.update(title, request.startAt(), request.endAt(), request.kind());
        journal.record(
                "event.update",
                Map.of("eventId", event.getId()),
                new EventUpdateInverse(
                        event.getId(),
                        previousTitle,
                        previousStart,
                        previousEnd,
                        previousKind),
                "Événement modifié : « " + event.getTitle() + " ».");
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(long id) {
        Event event = requireEvent(id);
        EventDeleteInverse inverse = new EventDeleteInverse(
                event.getId(),
                event.getTitle(),
                event.getStartAt(),
                event.getEndAt(),
                event.getKind(),
                event.getCreatedBy(),
                event.getTaskId());
        events.delete(event);
        journal.record(
                "event.delete",
                Map.of("eventId", id),
                inverse,
                "Événement supprimé : « " + inverse.title() + " ».");
    }

    private Event requireEvent(long id) {
        AppUser user = currentUser.requireCurrentUser();
        return events.findByIdAndUserId(id, user.getId())
                .orElseThrow(EventNotFoundException::new);
    }

    private void ensureNoConflict(AppUser user, Instant startAt, Instant endAt, Long excludedId, EventKind kind) {
        if (kind == EventKind.AVAILABILITY) {
            return;
        }

        events.findByUserIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                        user.getId(), endAt, startAt)
                .stream()
                .filter(event -> event.getKind() != EventKind.AVAILABILITY)
                .filter(event -> excludedId == null || !event.getId().equals(excludedId))
                .findFirst()
                .ifPresent(event -> {
                    throw new EventConflictException(event.getTitle());
                });
    }

    private String normalizedTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire.");
        }
        return title.trim();
    }

    private void requireKind(EventKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("Le type d'événement est obligatoire.");
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Les heures de début et de fin sont obligatoires.");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("La fin doit être après le début.");
        }
    }
}
