package com.calme.energy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.calme.journal.DeferredTask;
import com.calme.journal.EnergySetInverse;
import com.calme.journal.JournalRecorder;
import com.calme.tasks.Task;
import com.calme.tasks.TaskPriority;
import com.calme.tasks.TaskRepository;
import com.calme.tasks.TaskStatus;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnergyService {

    private final EnergyLogRepository logs;
    private final CurrentUserService currentUser;
    private final TaskRepository tasks;
    private final JournalRecorder journal;
    private final Clock clock;

    public EnergyService(
            EnergyLogRepository logs,
            CurrentUserService currentUser,
            TaskRepository tasks,
            JournalRecorder journal,
            Clock clock) {
        this.logs = logs;
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.journal = journal;
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
        EnergyLevel previousLevel = logs.findFirstByUserIdAndEnergyDateOrderByIdDesc(user.getId(), today)
                .map(EnergyLog::getLevel)
                .orElse(EnergyLevel.GOOD);

        List<DeferredTask> deferredTasks = new ArrayList<>();
        if (level == EnergyLevel.LOW) {
            List<Task> dueToday = tasks.findByUserIdAndStatusAndDueDateLessThanEqual(
                    user.getId(), TaskStatus.TODO, today);
            for (Task task : dueToday) {
                if (task.getPriority() != TaskPriority.CRITICAL) {
                    deferredTasks.add(new DeferredTask(task.getId(), task.getStatus(), task.getDueDate()));
                    task.deferTo(today.plusDays(1));
                }
            }
        }

        logs.save(new EnergyLog(user, today, level, EnergySource.TAP));
        journal.record(
                "energy.set",
                Map.of("level", level),
                new EnergySetInverse(today, previousLevel, deferredTasks),
                "Énergie mise à jour : " + label(level) + ".");
        return new EnergyResponse(today, level, deferredTasks.size());
    }

    private String label(EnergyLevel level) {
        return switch (level) {
            case LOW -> "basse";
            case MID -> "moyenne";
            case GOOD -> "bonne";
        };
    }

    private LocalDate today(AppUser user) {
        return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    }
}
