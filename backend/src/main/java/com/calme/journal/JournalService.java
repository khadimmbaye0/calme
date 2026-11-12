package com.calme.journal;

import java.util.List;

import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalService {

    private final AgentActionRepository actions;
    private final CurrentUserService currentUser;
    private final UndoService undoService;

    public JournalService(
            AgentActionRepository actions,
            CurrentUserService currentUser,
            UndoService undoService) {
        this.actions = actions;
        this.currentUser = currentUser;
        this.undoService = undoService;
    }

    @Transactional(readOnly = true)
    public List<JournalResponse> listRecent() {
        AppUser user = currentUser.requireCurrentUser();
        return listRecent(user);
    }

    @Transactional
    public List<JournalResponse> undo(long id) {
        AppUser user = currentUser.requireCurrentUser();
        AgentAction action = actions.findByIdAndUserId(id, user.getId())
                .orElseThrow(JournalNotFoundException::new);

        if (action.getStatus() != AgentActionStatus.APPLIED) {
            throw new JournalStateException("Cette action a déjà été annulée.");
        }

        AgentAction latest = actions
                .findFirstByUserIdAndStatusOrderByCreatedAtDescIdDesc(user.getId(), AgentActionStatus.APPLIED)
                .orElseThrow(() -> new JournalStateException("Aucune action à annuler."));
        if (!latest.getId().equals(action.getId())) {
            throw new JournalStateException("Seule la dernière action peut être annulée.");
        }

        undoService.undo(action);
        action.markUndone();
        return listRecent(user);
    }

    private List<JournalResponse> listRecent(AppUser user) {
        List<AgentAction> recent = actions.findTop30ByUserIdOrderByCreatedAtDescIdDesc(user.getId());
        Long latestAppliedId = actions
                .findFirstByUserIdAndStatusOrderByCreatedAtDescIdDesc(user.getId(), AgentActionStatus.APPLIED)
                .map(AgentAction::getId)
                .orElse(null);

        return recent.stream()
                .map(action -> new JournalResponse(
                        action.getId(),
                        action.getIntent(),
                        action.getExplanation(),
                        action.getStatus(),
                        action.getCreatedAt(),
                        action.getStatus() == AgentActionStatus.APPLIED
                                && action.getId().equals(latestAppliedId)))
                .toList();
    }
}
