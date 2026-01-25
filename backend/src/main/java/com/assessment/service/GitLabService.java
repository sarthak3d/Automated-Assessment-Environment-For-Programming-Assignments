package com.assessment.service;

import com.assessment.model.Course;
import com.assessment.model.User;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import org.gitlab4j.api.models.GroupParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GitLabService {

    private static final Logger log = LoggerFactory.getLogger(GitLabService.class);

    private final GitLabApi gitLabApi;

    @Value("${gitlab.default-visibility:private}")
    private String defaultVisibility;

    @Value("${gitlab.url}")
    private String gitlabUrl;

    public GitLabService(GitLabApi gitLabApi) {
        this.gitLabApi = gitLabApi;
    }

    public Long createOrGetUser(User user) throws GitLabApiException {
        List<org.gitlab4j.api.models.User> existingUsers = 
            gitLabApi.getUserApi().findUsers(user.getEmail());

        if (!existingUsers.isEmpty()) {
            return existingUsers.get(0).getId();
        }

        org.gitlab4j.api.models.User gitlabUser = new org.gitlab4j.api.models.User()
            .withUsername(user.getUsername())
            .withEmail(user.getEmail())
            .withName(user.getFullName())
            .withSkipConfirmation(true)
            .withCanCreateGroup(false)
            .withCanCreateProject(false);

        String tempPassword = generateTempPassword();
        org.gitlab4j.api.models.User created = gitLabApi.getUserApi().createUser(gitlabUser, tempPassword, false);
        
        log.info("Created GitLab user: {} (ID: {})", user.getUsername(), created.getId());
        return created.getId();
    }

    public Long createCourseGroup(Course course) throws GitLabApiException {
        String groupPath = "course-" + course.getCode().toLowerCase().replaceAll("[^a-z0-9]", "-");

        try {
            Group existingGroup = gitLabApi.getGroupApi().getGroup(groupPath);
            return existingGroup.getId();
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() != 404) {
                throw e;
            }
        }

        GroupParams groupParams = new GroupParams()
            .withName(course.getCode() + " - " + course.getName())
            .withPath(groupPath)
            .withDescription(course.getDescription())
            .withVisibility(defaultVisibility);

        Group created = gitLabApi.getGroupApi().createGroup(groupParams);
        log.info("Created GitLab group for course: {} (ID: {})", course.getCode(), created.getId());

        return created.getId();
    }

    public Long createAssignmentRepository(Course course, String assignmentTitle, String description) 
            throws GitLabApiException {
        if (course.getGitlabGroupId() == null) {
            throw new IllegalStateException("Course does not have a GitLab group");
        }

        String sanitizedPath = assignmentTitle.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (sanitizedPath.isBlank()) {
            sanitizedPath = "assignment";
        }
        String projectPath = sanitizedPath.substring(0, Math.min(sanitizedPath.length(), 50));

        Project project = new Project()
            .withName(assignmentTitle)
            .withPath(projectPath)
            .withDescription(description)
            .withNamespaceId(course.getGitlabGroupId())
            .withVisibility(Visibility.forValue(defaultVisibility))
            .withInitializeWithReadme(true);

        Project created = gitLabApi.getProjectApi().createProject(project);
        log.info("Created GitLab project for assignment: {} (ID: {})", assignmentTitle, created.getId());

        return created.getId();
    }

    public void addUserToGroup(Long groupId, Long userId, AccessLevel accessLevel) throws GitLabApiException {
        try {
            gitLabApi.getGroupApi().addMember(groupId, userId, accessLevel);
            log.debug("Added user {} to group {} with access level {}", userId, groupId, accessLevel);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 409) {
                log.debug("User {} already member of group {}", userId, groupId);
            } else {
                throw e;
            }
        }
    }

    public void addUserToProject(Long projectId, Long userId, AccessLevel accessLevel) throws GitLabApiException {
        try {
            gitLabApi.getProjectApi().addMember(projectId, userId, accessLevel);
            log.debug("Added user {} to project {} with access level {}", userId, projectId, accessLevel);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 409) {
                log.debug("User {} already member of project {}", userId, projectId);
            } else {
                throw e;
            }
        }
    }

    public void createStudentFolder(Long projectId, String studentId, String branchName) throws GitLabApiException {
        String folderPath = "students/" + studentId;
        String commitMessage = "Initialize folder for student " + studentId;

        try {
            RepositoryFile file = new RepositoryFile();
            file.setFilePath(folderPath + "/.gitkeep");
            file.setContent("");

            gitLabApi.getRepositoryFileApi().createFile(projectId, file, branchName, commitMessage);
            log.debug("Created student folder: {}", folderPath);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 400 && e.getMessage().contains("already exists")) {
                log.debug("Student folder already exists: {}", folderPath);
            } else {
                throw e;
            }
        }
    }

    public void commitFile(Long projectId, String filePath, String content, 
                          String commitMessage, String branch) throws GitLabApiException {
        String targetBranch = resolveGitReference(projectId, branch);
        try {
            gitLabApi.getRepositoryFileApi().getFile(projectId, filePath, targetBranch);
            RepositoryFile updateFile = new RepositoryFile();
            updateFile.setFilePath(filePath);
            updateFile.setContent(content);
            gitLabApi.getRepositoryFileApi().updateFile(projectId, updateFile,
                targetBranch, commitMessage);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404) {
                RepositoryFile createFile = new RepositoryFile();
                createFile.setFilePath(filePath);
                createFile.setContent(content);
                gitLabApi.getRepositoryFileApi().createFile(projectId, createFile,
                    targetBranch, commitMessage);
            } else {
                throw e;
            }
        }
    }

    public Long triggerPipeline(Long projectId, String ref) throws GitLabApiException {
        return triggerPipeline(projectId, ref, Map.of());
    }

    public Long triggerPipeline(Long projectId, String ref, Map<String, String> variables) throws GitLabApiException {
        String targetRef = resolveGitReference(projectId, ref);
        Pipeline pipeline = gitLabApi.getPipelineApi().createPipeline(projectId, targetRef, variables);
        log.info("Triggered pipeline {} for project {} on ref {}", pipeline.getId(), projectId, targetRef);
        return pipeline.getId();
    }

    public Pipeline getPipelineStatus(Long projectId, Long pipelineId) throws GitLabApiException {
        return gitLabApi.getPipelineApi().getPipeline(projectId, pipelineId);
    }

    public List<Job> getPipelineJobs(Long projectId, Long pipelineId) throws GitLabApiException {
        return gitLabApi.getJobApi().getJobsForPipeline(projectId, pipelineId);
    }

    public String getJobLog(Long projectId, Long jobId) throws GitLabApiException {
        return gitLabApi.getJobApi().getTrace(projectId, jobId);
    }

    public Optional<String> getFileContent(Long projectId, String filePath, String ref) {
        try {
            RepositoryFile file = gitLabApi.getRepositoryFileApi().getFile(projectId, filePath, ref);
            return Optional.of(file.getDecodedContentAsString());
        } catch (GitLabApiException e) {
            log.debug("File not found: {} in project {} ref {}", filePath, projectId, ref);
            return Optional.empty();
        }
    }

    private String generateTempPassword() {
        return java.util.UUID.randomUUID().toString().substring(0, 16) + "Aa1!";
    }

    private String resolveGitReference(Long projectId, String preferredRef) throws GitLabApiException {
        if (preferredRef != null && !preferredRef.isBlank() && branchExists(projectId, preferredRef)) {
            return preferredRef;
        }

        String defaultBranch = gitLabApi.getProjectApi().getProject(projectId).getDefaultBranch();
        if (defaultBranch != null && !defaultBranch.isBlank() && branchExists(projectId, defaultBranch)) {
            return defaultBranch;
        }

        for (String candidate : List.of("main", "master")) {
            if (branchExists(projectId, candidate)) {
                return candidate;
            }
        }

        throw new GitLabApiException("No valid branch found for project " + projectId);
    }

    private boolean branchExists(Long projectId, String branch) throws GitLabApiException {
        try {
            gitLabApi.getRepositoryApi().getBranch(projectId, branch);
            return true;
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404) {
                return false;
            }
            throw e;
        }
    }
}
