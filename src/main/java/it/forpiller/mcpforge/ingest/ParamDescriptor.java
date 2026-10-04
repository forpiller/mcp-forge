package it.forpiller.mcpforge.ingest;

public record ParamDescriptor(
        String name,
        ParamLocation location,
        boolean required,
        String jsonSchemaType,
        String description
) {
}
