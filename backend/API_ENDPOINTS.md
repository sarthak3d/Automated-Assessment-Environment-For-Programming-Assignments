# Assessment Platform Backend API Documentation

This document describes the REST API endpoints available in the Assessment Platform backend.

## Base URL
All API endpoints are relative to:
`/api/v1`

---

## 1. Authentication (`/auth`)

Endpoints for user authentication and session management.

### Handle SSO Callback
- **URL**: `/auth/sso/callback`
- **Method**: `POST`
- **Description**: Handles the callback from the Single Sign-On provider (receives JWT).
- **Responses**: 
  - `200 OK`: Returns an `AuthResponse` object.

### Refresh Token
- **URL**: `/auth/refresh`
- **Method**: `POST`
- **Headers**: 
  - `Authorization: Bearer <token>`
- **Description**: Refreshes the authentication token.
- **Responses**:
  - `200 OK`: Returns a new `AuthResponse` object.

### Logout
- **URL**: `/auth/logout`
- **Method**: `POST`
- **Headers**:
  - `Authorization: Bearer <token>`
- **Description**: Logs out the current user and invalidates their session.
- **Responses**:
  - `204 No Content`: Successful logout.

### Get Current User
- **URL**: `/auth/me`
- **Method**: `GET`
- **Headers**:
  - `Authorization: Bearer <token>`
- **Description**: Returns the profile information of the currently authenticated user.
- **Responses**:
  - `200 OK`: Returns a `UserDto` object.
  - `404 Not Found`: If the user is not found.

---

## 2. Courses (`/courses`)

Endpoints for managing courses and student enrollments.

### List Courses
- **URL**: `/courses`
- **Method**: `GET`
- **Query Parameters**: Pageable properties (`page`, `size`, `sort`)
- **Description**: Returns a paginated list of active courses. Behavior differs by role:
  - **Admin**: All active courses.
  - **Teacher**: Courses they instruct.
  - **Student**: Courses they are enrolled in.
- **Responses**:
  - `200 OK`: Returns a `Page<Course>`.

### Get Course by ID
- **URL**: `/courses/{id}`
- **Method**: `GET`
- **Description**: Get details of a specific course by its UUID.
- **Responses**:
  - `200 OK`: Returns the `Course` object.
  - `404 Not Found`

### Create Course (Admin or Teacher Role)
- **URL**: `/courses`
- **Method**: `POST`
- **Body** (JSON): `CreateCourseRequest`
  ```json
  {
    "code": "CS101",
    "name": "Introduction to Computer Science",
    "description": "Basic concepts...",
    "semester": "Fall",
    "year": 2024
  }
  ```
- **Description**: Creates a new course and provisions a corresponding GitLab group.
- **Responses**:
  - `201 Created`: Returns the created `Course`.
  - `400 Bad Request`: If course code already exists.

### Enroll Student (Admin or Teacher Role)
- **URL**: `/courses/{id}/enroll`
- **Method**: `POST`
- **Body** (JSON): `EnrollStudentRequest`
  ```json
  {
    "studentId": "uuid-here"
  }
  ```
- **Description**: Enrolls a student in a course and grants developer access to the course's GitLab group.
- **Responses**:
  - `200 OK`: Successfully enrolled.
  - `400 Bad Request`: If the user is not a student.
  - `404 Not Found`: If course or student is not found.

### List Students in Course (Admin or Teacher Role)
- **URL**: `/courses/{id}/students`
- **Method**: `GET`
- **Query Parameters**: Pageable properties
- **Description**: Returns a paginated list of students enrolled in the specified course.
- **Responses**:
  - `200 OK`: Returns a `Page<UserDto>`.

---

## 3. Assignments (`/courses/{courseId}/assignments`)

Endpoints for managing assignments within a course. 
All paths use the prefix `/courses/{courseId}/assignments`.

### List Assignments
- **URL**: `/`
- **Method**: `GET`
- **Query Parameters**: Pageable properties
- **Description**: Returns a paginated list of assignments for the course. Students only see `PUBLISHED` assignments, while teachers/admins see all.
- **Responses**:
  - `200 OK`: Returns a `Page<Assignment>`.

### Get Assignment
- **URL**: `/{id}`
- **Method**: `GET`
- **Description**: Get details of a specific assignment by its UUID.
- **Responses**:
  - `200 OK`: Returns the `Assignment` object.
  - `404 Not Found`

### Create Assignment (Admin or Teacher Role)
- **URL**: `/`
- **Method**: `POST`
- **Body** (JSON): `CreateAssignmentRequest`
  ```json
  {
    "title": "Assignment 1",
    "description": "Description here",
    "dueDate": "2024-12-01T23:59:59Z",
    "maxSubmissions": 10,
    "allowLateSubmissions": false,
    "testModuleIds": ["uuid-1", "uuid-2"]
  }
  ```
- **Description**: Creates a new assignment in `DRAFT` status and creates a GitLab repository project for it. optionally configures default test modules.
- **Responses**:
  - `201 Created`: Returns the created `Assignment`.
  - `404 Not Found`: If the course does not exist.

### Update Assignment (Admin or Teacher Role)
- **URL**: `/{id}`
- **Method**: `PUT`
- **Body** (JSON): `UpdateAssignmentRequest` (same fields as Create, optional)
- **Description**: Updates fields of an existing assignment.
- **Responses**:
  - `200 OK`: Returns the updated `Assignment`.
  - `404 Not Found`

### Publish Assignment (Admin or Teacher Role)
- **URL**: `/{id}/publish`
- **Method**: `POST`
- **Description**: Changes the assignment status to `PUBLISHED`, generates CI configuration YAML based on test module weights, and commits it to the GitLab repository.
- **Responses**:
  - `200 OK`: Returns the updated `Assignment`.
  - `400 Bad Request`: If the assignment is not in `DRAFT` status.
  - `404 Not Found`

### Configure Test Modules (Admin or Teacher Role)
- **URL**: `/{id}/test-modules`
- **Method**: `POST`
- **Body** (JSON Array): List of `TestModuleConfigRequest`
  ```json
  [
    {
      "testModuleId": "uuid-here",
      "weight": 50.0,
      "enabled": true,
      "customConfig": "config string",
      "timeoutSeconds": 300,
      "memoryLimitMb": 512
    }
  ]
  ```
- **Description**: Overwrites configuring test modules and assigning corresponding weights for an assignment.
- **Responses**:
  - `200 OK`: Reconfigured successfully.

---

## 4. Submissions (`/courses/{courseId}/assignments/{assignmentId}/submissions`)

Endpoints for student submissions.
All paths use the prefix `/courses/{courseId}/assignments/{assignmentId}/submissions`.

### List Submissions
- **URL**: `/`
- **Method**: `GET`
- **Query Parameters**: Pageable properties
- **Description**: Returns paginated submissions. Students see only their own, while teachers/admins see all submissions for the assignment.
- **Responses**:
  - `200 OK`: Returns a `Page<Submission>`.

### Get Submission
- **URL**: `/{id}`
- **Method**: `GET`
- **Description**: Get details of a specific submission. Respects access control (students can't access others' submissions).
- **Responses**:
  - `200 OK`: Returns the `Submission` object.
  - `404 Not Found`

### Create Submission
- **URL**: `/`
- **Method**: `POST`
- **Body** (JSON): `CreateSubmissionRequest`
  ```json
  {
    "files": {
      "Solution.java": "public class Solution { ... }"
    }
  }
  ```
- **Description**: Submits code for an assignment. Checks deadlines, max attempt count, ensures the assignment is published, commits files to GitLab, and triggers the CI pipeline.
- **Responses**:
  - `201 Created`: Returns the created `Submission` object (status `QUEUED` or `ERROR`).
  - `400 Bad Request`: If max submissions exceeded, deadline passed, or assignment not open.
  - `404 Not Found`

### Get Submission Grade
- **URL**: `/{id}/grade`
- **Method**: `GET`
- **Description**: Fetches the calculated grade of a submission. Resolves visibility rules.
- **Responses**:
  - `200 OK`: Returns the `Grade` object or a pending JSON status if not yet calculated.
  - `404 Not Found`

### Trigger Regrade (Admin or Teacher Role)
- **URL**: `/{id}/regrade`
- **Method**: `POST`
- **Description**: Manually triggers the grading service for a particular submission.
- **Responses**:
  - `202 Accepted`
  - `404 Not Found`

### Get My Latest Submission
- **URL**: `/my-latest`
- **Method**: `GET`
- **Description**: Returns the authenticated user's latest submission attempt for this assignment.
- **Responses**:
  - `200 OK`: Returns the `Submission` object.
  - `204 No Content`: If no submission exists.

---

## 5. Webhooks (`/webhooks`)

GitLab system webhook callbacks used mostly by the CI/CD integration. No manual calls intended.

### Handle Pipeline Webhook
- **URL**: `/webhooks/pipeline`
- **Method**: `POST`
- **Headers**:
  - `X-Gitlab-Token: <token>` (Optional)
- **Description**: Triggered by GitLab when a pipeline status changes. Used to update the `pipelineResultService`.
- **Responses**: `200 OK`

### Handle Job Webhook
- **URL**: `/webhooks/job`
- **Method**: `POST`
- **Headers**:
  - `X-Gitlab-Token: <token>` (Optional)
- **Description**: Triggered by GitLab when a job status changes.
- **Responses**: `200 OK`

### Handle Pipeline Complete Callback
- **URL**: `/webhooks/pipeline-complete`
- **Method**: `POST`
- **Description**: Custom callback used when a CI pipeline fully completes execution.
- **Responses**: `200 OK`
