package com.calme.calendar;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        Instant startAt,
        Instant endAt,
        EventKind kind,
        EventCreator createdBy) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getStartAt(),
                event.getEndAt(),
                event.getKind(),
                event.getCreatedBy());
    }
}
