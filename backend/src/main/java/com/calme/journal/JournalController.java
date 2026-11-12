package com.calme.journal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final JournalService journal;

    public JournalController(JournalService journal) {
        this.journal = journal;
    }

    @GetMapping
    public List<JournalResponse> list() {
        return journal.listRecent();
    }

    @PostMapping("/{id}/undo")
    public List<JournalResponse> undo(@PathVariable long id) {
        return journal.undo(id);
    }
}
