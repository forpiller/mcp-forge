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
    public OperationDescriptor withToolName(String newToolName) {
        return new OperationDescriptor(newToolName, httpMethod, path, summary, parameters, hasRequestBody, requestBodyDescription);
    }
}
