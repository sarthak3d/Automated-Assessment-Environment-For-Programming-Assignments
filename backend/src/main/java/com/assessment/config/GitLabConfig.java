package com.assessment.config;

import org.gitlab4j.api.GitLabApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitLabConfig {

    @Value("${gitlab.url}")
    private String gitlabUrl;

    @Value("${GITLAB_ADMIN_TOKEN:}")
    private String gitlabAdminToken;

    @Value("${gitlab.api-version:v4}")
    private String apiVersion;

    @Value("${gitlab.connect-timeout-ms:60000}")
    private Integer connectTimeoutMs;

    @Value("${gitlab.read-timeout-ms:300000}")
    private Integer readTimeoutMs;

    @Bean
    public GitLabApi gitLabApi() {
        GitLabApi gitLabApi = new GitLabApi(gitlabUrl, gitlabAdminToken);
        gitLabApi.setRequestTimeout(connectTimeoutMs, readTimeoutMs);
        gitLabApi.enableRequestResponseLogging();
        return gitLabApi;
    }
}
