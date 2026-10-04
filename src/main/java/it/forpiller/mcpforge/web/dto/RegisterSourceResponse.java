package it.forpiller.mcpforge.web.dto;

import java.util.List;

public record RegisterSourceResponse(
        String sourceName,
        int toolsGenerated,
        List<String> toolNames
) {
}
