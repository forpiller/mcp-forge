package it.forpiller.mcpforge.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pagina di sola lettura: comandi pronti (porta già compilata) per collegare
 * un client MCP vero (Claude Code, Claude Desktop) a questo server — niente
 * di eseguibile da qui, solo testo pronto da copiare nel proprio terminale.
 */
@Controller
public class ConnectController {

    @Value("${server.port}")
    private int serverPort;

    @GetMapping("/connetti")
    public String connetti(Model model) {
        String mcpUrl = "http://localhost:" + serverPort + "/mcp";

        model.addAttribute("mcpUrl", mcpUrl);
        model.addAttribute("claudeCodeCommand", "claude mcp add --transport http mcp-forge " + mcpUrl);
        model.addAttribute("claudeCodeAllowCommand",
                "jq '.permissions.allow = ((.permissions.allow // []) + [\"mcp__mcp-forge__*\"] | unique)' " +
                "~/.claude/settings.json > /tmp/_mf.json && mv /tmp/_mf.json ~/.claude/settings.json");
        model.addAttribute("desktopConfigSnippet", """
                "mcp-forge": {
                  "transport": "streamable-http",
                  "url": "%s"
                }""".formatted(mcpUrl));

        model.addAttribute("geminiSettingsSnippet", """
                {
                  "mcpServers": {
                    "mcp-forge": {
                      "httpUrl": "%s",
                      "timeout": 30000
                    }
                  }
                }""".formatted(mcpUrl));

        model.addAttribute("vscodeMcpSnippet", """
                {
                  "servers": {
                    "mcp-forge": {
                      "type": "http",
                      "url": "%s"
                    }
                  }
                }""".formatted(mcpUrl));

        model.addAttribute("cursorMcpSnippet", """
                {
                  "mcpServers": {
                    "mcp-forge": {
                      "url": "%s"
                    }
                  }
                }""".formatted(mcpUrl));

        model.addAttribute("codexConfigSnippet", """
                [[mcp_servers]]
                name = "mcp-forge"
                transport = "http"
                url = "%s"
                """.formatted(mcpUrl));

        return "connetti";
    }
}
