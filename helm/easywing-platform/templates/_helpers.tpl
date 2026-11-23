{{/*
基础名称与标签。服务名使用固定名称（auth-service 等），
保证容器内 DNS 与网关 lb:// 路由引用稳定。
*/}}
{{- define "easywing.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "easywing.labels" -}}
app.kubernetes.io/name: {{ include "easywing.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: easywing-platform
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- with .Values.global.labels -}}
{{ toYaml . }}
{{- end -}}
{{- end -}}

{{- define "easywing.selectorLabels" -}}
app.kubernetes.io/name: {{ include "easywing.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "easywing.image" -}}
{{- if .digest -}}
{{- printf "%s@%s" .repository .digest -}}
{{- else -}}
{{- printf "%s:%s" .repository .tag -}}
{{- end -}}
{{- end -}}

{{- define "easywing.pullPolicy" -}}
{{- .Values.global.imagePullPolicy -}}
{{- end -}}

{{- define "easywing.storageClass" -}}
{{- if .Values.global.storageClassName -}}
{{- .Values.global.storageClassName -}}
{{- else -}}
""
{{- end -}}
{{- end -}}

{{- define "easywing.nacos.mysqlPassword" -}}
{{- if .Values.infra.nacos.mysql.password -}}
{{- .Values.infra.nacos.mysql.password -}}
{{- else -}}
{{- .Values.infra.mysql.rootPassword -}}
{{- end -}}
{{- end -}}

{{/*
机器客户端（client_credentials）注入为 SPRING_APPLICATION_JSON，
使 easywing.auth.oauth2.machine-clients.<name> 生效。
*/}}
{{- define "easywing.machineClientsJson" -}}
{{- $obj := dict "easywing" (dict "auth" (dict "oauth2" (dict "machine-clients" .Values.secrets.machineClients))) -}}
{{- $obj | toJson -}}
{{- end -}}