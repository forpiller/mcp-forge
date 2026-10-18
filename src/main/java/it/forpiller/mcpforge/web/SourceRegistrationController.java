package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.service.SourceRegistrationService;
import it.forpiller.mcpforge.web.dto.RegisterSourceRequest;
import it.forpiller.mcpforge.web.dto.RegisterSourceResponse;
import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class SourceRegistrationController {

    private final SourceRegistrationService sourceRegistrationService;

    public SourceRegistrationController(SourceRegistrationService sourceRegistrationService) {
        this.sourceRegistrationService = sourceRegistrationService;
    }

    @PostMapping
    public ResponseEntity<RegisterSourceResponse> register(@Valid @RequestBody RegisterSourceRequest request) {
        SourceRegistrationInput input = new SourceRegistrationInput(
                request.name(), request.openApiUrl(), request.baseUrl(),
                request.authHeaderName(), request.authHeaderValue(),
                request.oauth2TokenUrl(), request.oauth2ClientId(), request.oauth2ClientSecret(),
                request.oauth2Username(), request.oauth2Password(),
                null, null, null, null);

        List<String> toolNames = sourceRegistrationService.register(input);
        return ResponseEntity.ok(new RegisterSourceResponse(request.name(), toolNames.size(), toolNames));
    }
}
