package it.forpiller.mcpforge.ingest;

import java.util.List;

public record OperationDescriptor(
        String toolName,
        String httpMethod,
        String path,
        String summary,
        List<ParamDescriptor> parameters,
        boolean hasRequestBody,
        String requestBodyDescription
) {
}
