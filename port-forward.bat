@echo off
echo Starting port forwarding for Assessment Platform services...

echo Forwarding Backend (http://localhost:8081) ...
start "Backend Port Forward" cmd /c "kubectl port-forward svc/assessment-assessment-platform-backend 8081:8080 -n assessment-platform"

echo.
echo Port forwarding started in separate background windows.
echo You can access the services at:
echo - Backend API:     http://localhost:8081/actuator/health
echo.
echo Close the newly opened command prompt windows to stop port forwarding.
pause
