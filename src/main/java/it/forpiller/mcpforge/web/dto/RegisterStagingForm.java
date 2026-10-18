package it.forpiller.mcpforge.web.dto;

public class RegisterStagingForm {

    private String stagingBaseUrl = "";
    private String stagingName = "";
    private String stagingOpenApiUrl = "";
    private String stagingLoginUrl = "";
    private String stagingUsername = "";
    private String stagingPassword = "";

    public String getStagingBaseUrl() { return stagingBaseUrl; }
    public void setStagingBaseUrl(String v) { this.stagingBaseUrl = v; }

    public String getStagingName() { return stagingName; }
    public void setStagingName(String v) { this.stagingName = v; }

    public String getStagingOpenApiUrl() { return stagingOpenApiUrl; }
    public void setStagingOpenApiUrl(String v) { this.stagingOpenApiUrl = v; }

    public String getStagingLoginUrl() { return stagingLoginUrl; }
    public void setStagingLoginUrl(String v) { this.stagingLoginUrl = v; }

    public String getStagingUsername() { return stagingUsername; }
    public void setStagingUsername(String v) { this.stagingUsername = v; }

    public String getStagingPassword() { return stagingPassword; }
    public void setStagingPassword(String v) { this.stagingPassword = v; }
}
