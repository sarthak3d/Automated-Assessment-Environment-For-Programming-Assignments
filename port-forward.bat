@echo off
echo Starting port forwarding for Assessment Platform services...

echo Forwarding Backend (http://localhost:8081) ...
start "Backend Port Forward" cmd /c "kubectl port-forward svc/assessment-assessment-platform-backend 8081:8080 -n assessment-platform"

echo Forwarding GitLab (http://localhost:8929) ...
start "GitLab Port Forward" cmd /c "kubectl port-forward svc/assessment-assessment-platform-gitlab 8929:80 -n assessment-platform"

echo Forwarding Module Gateway (http://localhost:8090) ...
start "Module Gateway Port Forward" cmd /c "kubectl port-forward svc/module-gateway 8090:80 -n assessment-platform"

echo.
echo Port forwarding started in separate background windows.
echo You can access the services at:
echo - Backend API:     http://localhost:8081/actuator/health
echo - GitLab CE:       http://localhost:8929
echo - Module Gateway:  http://localhost:8090/health
echo.
echo Close the newly opened command prompt windows to stop port forwarding.
pause
