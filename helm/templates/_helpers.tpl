{{/*
Expand the name of the chart.
*/}}
{{- define "assessment-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "assessment-platform.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "assessment-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "assessment-platform.labels" -}}
helm.sh/chart: {{ include "assessment-platform.chart" . }}
{{ include "assessment-platform.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "assessment-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ include "assessment-platform.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Backend selector labels
*/}}
{{- define "assessment-platform.backend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "assessment-platform.name" . }}-backend
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: backend
{{- end }}

{{/*
GitLab selector labels
*/}}
{{- define "assessment-platform.gitlab.selectorLabels" -}}
app.kubernetes.io/name: {{ include "assessment-platform.name" . }}-gitlab
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: gitlab
{{- end }}

{{/*
GitLab Runner selector labels
*/}}
{{- define "assessment-platform.runner.selectorLabels" -}}
app.kubernetes.io/name: {{ include "assessment-platform.name" . }}-runner
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: gitlab-runner
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "assessment-platform.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "assessment-platform.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
PostgreSQL host
*/}}
{{- define "assessment-platform.postgresql.host" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "%s-postgresql" .Release.Name }}
{{- else }}
{{- .Values.externalPostgresql.host }}
{{- end }}
{{- end }}

{{/*
Redis host
*/}}
{{- define "assessment-platform.redis.host" -}}
{{- if .Values.redis.enabled }}
{{- printf "%s-redis-master" .Release.Name }}
{{- else }}
{{- .Values.externalRedis.host }}
{{- end }}
{{- end }}

{{/*
RabbitMQ host
*/}}
{{- define "assessment-platform.rabbitmq.host" -}}
{{- if .Values.rabbitmq.enabled }}
{{- printf "%s-rabbitmq" .Release.Name }}
{{- else if .Values.externalRabbitmq }}
{{- .Values.externalRabbitmq.host }}
{{- else }}
{{- print "localhost" }}
{{- end }}
{{- end }}

{{/*
GitLab internal URL
*/}}
{{- define "assessment-platform.gitlab.internalUrl" -}}
{{- printf "http://%s-gitlab:80" (include "assessment-platform.fullname" .) }}
{{- end }}

{{/*
GitLab internal FQDN URL (for cross-namespace access from runner job pods)
*/}}
{{- define "assessment-platform.gitlab.internalUrlFQDN" -}}
{{- printf "http://%s-gitlab.%s.svc.cluster.local:80" (include "assessment-platform.fullname" .) .Values.global.namespace }}
{{- end }}
