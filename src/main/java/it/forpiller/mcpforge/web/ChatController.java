package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.chat.ChatToolCallbackFactory;
import it.forpiller.mcpforge.chat.ChatTurn;
import it.forpiller.mcpforge.mcp.RegisteredTool;
import it.forpiller.mcpforge.mcp.SourceRegistry;
import it.forpiller.mcpforge.service.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    private static final String SYSTEM_PROMPT = """
            Sei un assistente collegato a delle API reali tramite dei tool.
            REGOLA FONDAMENTALE: non inventare MAI dati (nomi, numeri, elenchi, stati) di
            tua iniziativa. Se la domanda riguarda animali, ordini, utenti, profili o
            qualsiasi altra cosa gestita dai tool disponibili, DEVI chiamare il tool
            corrispondente e usare solo il risultato vero che restituisce — anche per
            domande generiche tipo "dimmi dei nomi" o "fammi un esempio".
            Se nessun tool disponibile copre la domanda, dillo esplicitamente invece di
            rispondere a memoria.
            Rispondi sempre in italiano, in modo breve e diretto.""";

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private final ChatClient chatClient;
    private final ChatHistoryService historyService;
    private final ChatToolCallbackFactory toolCallbackFactory;
    private final SourceRegistry sourceRegistry;

    public ChatController(
            ChatClient.Builder chatClientBuilder,
            ChatHistoryService historyService,
            ChatToolCallbackFactory toolCallbackFactory,
            SourceRegistry sourceRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.historyService = historyService;
        this.toolCallbackFactory = toolCallbackFactory;
        this.sourceRegistry = sourceRegistry;
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("ollamaAvailable", isOllamaAvailable());
        model.addAttribute("turns", historyService.all());
        model.addAttribute("sourceGroups", groupBySource());
        model.addAttribute("selectedTools", currentSelection());
        return "chat";
    }

    @PostMapping("/chat")
    public String send(
            @RequestParam String message,
            @RequestParam(required = false) List<String> tools) {
        Set<String> selected = tools != null ? new LinkedHashSet<>(tools) : Set.of();
        historyService.setSelectedToolNames(selected);

        if (message != null && !message.isBlank()) {
            List<Message> conversation = buildConversation(message);
            historyService.add(new ChatTurn("user", message));

            try {
                String reply = chatClient.prompt()
                        .messages(conversation)
                        .toolCallbacks(toolCallbackFactory.buildCallbacks(selected))
                        .call()
                        .content();
                historyService.add(new ChatTurn("assistant", reply != null ? reply : "(nessuna risposta)"));
            } catch (Exception e) {
                historyService.add(new ChatTurn("error", "Errore: " + e.getMessage()));
            }
        }

        return "redirect:/chat";
    }

    @PostMapping("/chat/pulisci")
    public String clear() {
        historyService.clear();
        return "redirect:/chat";
    }

    private List<Message> buildConversation(String newUserMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        for (ChatTurn turn : historyService.all()) {
            switch (turn.role()) {
                case "user" -> messages.add(new UserMessage(turn.content()));
                case "assistant" -> messages.add(new AssistantMessage(turn.content()));
                default -> { /* i turni "error" non fanno parte della conversazione vera */ }
            }
        }

        messages.add(new UserMessage(newUserMessage));
        return messages;
    }

    private Set<String> currentSelection() {
        Set<String> saved = historyService.getSelectedToolNames();
        if (saved != null) {
            return saved;
        }
        return sourceRegistry.all().stream().map(RegisteredTool::fullToolName).collect(Collectors.toSet());
    }

    private Map<String, List<RegisteredTool>> groupBySource() {
        return sourceRegistry.all().stream()
                .collect(Collectors.groupingBy(t -> t.source().name(), LinkedHashMap::new, Collectors.toList()));
    }

    private boolean isOllamaAvailable() {
        try {
            URI uri = URI.create(ollamaBaseUrl);
            int port = uri.getPort() > 0 ? uri.getPort() : 11434;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(uri.getHost(), port), 600);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
