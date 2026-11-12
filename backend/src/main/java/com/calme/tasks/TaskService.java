package com.calme.tasks;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.calme.journal.JournalRecorder;
import com.calme.journal.TaskCreateInverse;
import com.calme.journal.TaskStateInverse;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository tasks;
    private final CurrentUserService currentUser;
    private final JournalRecorder journal;
    private final Clock clock;

    public TaskService(
            TaskRepository tasks,
            CurrentUserService currentUser,
            JournalRecorder journal,
            Clock clock) {
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listOpen() {
        AppUser user = currentUser.requireCurrentUser();
        return tasks
                .findByUserIdAndStatusInOrderByDueDateAscPriorityAscEstimateMaxAsc(
                        user.getId(), List.of(TaskStatus.TODO, TaskStatus.DOING))
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        AppUser user = currentUser.requireCurrentUser();
        String title = normalizeTitle(request.title());
        validateEstimates(request.estimateMin(), request.estimateMax());
        if (request.priority() == null) {
            throw new IllegalArgumentException("La priorité est obligatoire.");
        }

        LocalDate dueDate = request.dueDate() == null ? today(user) : request.dueDate();
        Task task = tasks.save(new Task(
                user,
                title,
                request.estimateMin(),
                request.estimateMax(),
                request.priority(),
                dueDate));
        journal.record(
                "task.create",
                Map.of("taskId", task.getId()),
                new TaskCreateInverse(task.getId()),
                "Tâche ajoutée : « " + task.getTitle() + " ».");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse start(long id) {
        Task task = requireTask(id);
        if (task.getStatus() != TaskStatus.TODO) {
            throw new TaskStateException("Cette tâche a déjà changé d'état.");
        }
        TaskStateInverse inverse = new TaskStateInverse(task.getId(), task.getStatus(), task.getDueDate());
        task.start();
        journal.record(
                "task.start",
                Map.of("taskId", task.getId()),
                inverse,
                "Tâche démarrée : « " + task.getTitle() + " ».");
        return TaskResponse.from(task);
    }

    @Transactional
    public void done(long id) {
        Task task = requireTask(id);
        if (task.getStatus() == TaskStatus.DONE) {
            return;
        }
        TaskStateInverse inverse = new TaskStateInverse(task.getId(), task.getStatus(), task.getDueDate());
        task.done();
        journal.record(
                "task.done",
                Map.of("taskId", task.getId()),
                inverse,
                "Tâche terminée : « " + task.getTitle() + " ».");
    }

    @Transactional
    public void deferToTomorrow(long id) {
        Task task = requireTask(id);
        TaskStateInverse inverse = new TaskStateInverse(task.getId(), task.getStatus(), task.getDueDate());
        task.deferTo(today(task.getUser()).plusDays(1));
        journal.record(
                "task.defer",
                Map.of("taskId", task.getId()),
                inverse,
                "Tâche reportée : « " + task.getTitle() + " ».");
    }

    private Task requireTask(long id) {
        AppUser user = currentUser.requireCurrentUser();
        return tasks.findByIdAndUserId(id, user.getId())
                .orElseThrow(TaskNotFoundException::new);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire.");
        }
        return title.trim();
    }

    private void validateEstimates(int estimateMin, int estimateMax) {
        if (estimateMin <= 0) {
            throw new IllegalArgumentException("L'estimation minimale doit être positive.");
        }
        if (estimateMax < estimateMin) {
            throw new IllegalArgumentException("L'estimation maximale doit être supérieure ou égale à l'estimation minimale.");
        }
    }

    private LocalDate today(AppUser user) {
        return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    }
}
