package com.calme.journal;

import java.time.Instant;

import com.calme.calendar.EventKind;

public record EventUpdateInverse(
        Long eventId,
        String title,
        Instant startAt,
        Instant endAt,
        EventKind kind) {
}
