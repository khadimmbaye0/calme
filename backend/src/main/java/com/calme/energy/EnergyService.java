package com.calme.energy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import com.calme.tasks.TaskService;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnergyService {

    private final EnergyLogRepository logs;
    private final CurrentUserService currentUser;
    private final TaskService tasks;
    private final Clock clock;

    public EnergyService(
            EnergyLogRepository logs,
            CurrentUserService currentUser,
            TaskService tasks,
            Clock clock) {
        this.logs = logs;
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EnergyResponse currentToday() {
        AppUser user = currentUser.requireCurrentUser();
        LocalDate today = today(user);
        EnergyLevel level = logs.findFirstByUserIdAndEnergyDateOrderByIdDesc(user.getId(), today)
                .map(EnergyLog::getLevel)
                .orElse(EnergyLevel.GOOD);
        return new EnergyResponse(today, level, 0);
    }

    @Transactional
    public EnergyResponse setToday(EnergyLevel level) {
        AppUser user = currentUser.requireCurrentUser();
        LocalDate today = today(user);
        logs.save(new EnergyLog(user, today, level, EnergySource.TAP));

        int deferred = level == EnergyLevel.LOW ? tasks.deferNonCritical(today) : 0;
        return new EnergyResponse(today, level, deferred);
    }

    private LocalDate today(AppUser user) {
        return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    }
}
