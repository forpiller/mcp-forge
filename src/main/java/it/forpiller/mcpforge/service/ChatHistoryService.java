package it.forpiller.mcpforge.service;

import it.forpiller.mcpforge.chat.ChatTurn;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatHistoryService {

    private final List<ChatTurn> turns = new CopyOnWriteArrayList<>();

    /** null = nessuna selezione ancora fatta esplicitamente → di default tutti i tool. */
    private volatile Set<String> selectedToolNames = null;

    public void add(ChatTurn turn) {
        turns.add(turn);
    }

    public List<ChatTurn> all() {
        return Collections.unmodifiableList(turns);
    }

    public void clear() {
        turns.clear();
    }

    public Set<String> getSelectedToolNames() {
        return selectedToolNames;
    }

    public void setSelectedToolNames(Set<String> selectedToolNames) {
        this.selectedToolNames = selectedToolNames;
    }
}
