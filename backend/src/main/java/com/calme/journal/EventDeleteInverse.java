package com.calme.journal;

import java.time.Instant;

import com.calme.calendar.EventCreator;
import com.calme.calendar.EventKind;

public record EventDeleteInverse(
        Long eventId,
        String title,
        Instant startAt,
        Instant endAt,
        EventKind kind,
        EventCreator createdBy,
        Long taskId) {
}
