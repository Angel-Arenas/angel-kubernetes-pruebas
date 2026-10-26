# ==============================================================================
# Script de Despliegue Automatizado (PowerShell) - Bodega Peirano
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   INICIANDO SETUP: BODEGA PEIRANO MICROSERVICIOS KUBERNETES    " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Verificacion de herramientas locales
Write-Host "`n[1/7] Verificando herramientas locales..." -ForegroundColor Yellow
$requiredCommands = @("docker", "minikube", "kubectl", "helm", "mvn")
foreach ($cmd in $requiredCommands) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Host "[ERROR] Herramienta no encontrada en PATH: $cmd" -ForegroundColor Red
        exit 1
    }
    Write-Host "  [OK] $cmd detectado" -ForegroundColor Green
}

# 2. Iniciar Minikube si no esta en ejecucion
Write-Host "`n[2/7] Verificando estado de Minikube..." -ForegroundColor Yellow
$status = minikube status 2>&1 | Out-String
if ($status -notmatch "Running") {
    Write-Host "Iniciando Minikube con driver docker..." -ForegroundColor Cyan
    minikube start --driver=docker --cpus=2 --memory=4096
} else {
    Write-Host "Minikube ya se encuentra en ejecucion." -ForegroundColor Green
}

# 3. Habilitar addon metrics-server
Write-Host "`n[3/7] Habilitando addon metrics-server en Minikube..." -ForegroundColor Yellow
minikube addons enable metrics-server

# 4. Instalar o actualizar kube-prometheus-stack mediante Helm
Write-Host "`n[4/7] Configurando Stack de Monitoreo Prometheus y Grafana..." -ForegroundColor Yellow
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

Write-Host "Instalando / Actualizando kube-prometheus-stack con valores personalizados..." -ForegroundColor Cyan
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack `
    --namespace monitoring `
    --create-namespace `
    -f k8s/values-prometheus.yaml

# 5. Configurar Docker daemon de Minikube y compilar imagenes
Write-Host "`n[5/7] Configurando Docker daemon de Minikube y construyendo imagenes..." -ForegroundColor Yellow
Invoke-Expression (minikube docker-env --shell powershell)

Write-Host "Construyendo imagen: catalogo-service:latest..." -ForegroundColor Cyan
docker build -t catalogo-service:latest catalogo-service/

Write-Host "Construyendo imagen: pedidos-service:latest..." -ForegroundColor Cyan
docker build -t pedidos-service:latest pedidos-service/

# 6. Desplegar manifiestos de Kubernetes
Write-Host "`n[6/7] Aplicando manifiestos en namespace bodega-peirano..." -ForegroundColor Yellow
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/catalogo-deployment.yaml
kubectl apply -f k8s/catalogo-service.yaml
kubectl apply -f k8s/catalogo-servicemonitor.yaml
kubectl apply -f k8s/pedidos-deployment.yaml
kubectl apply -f k8s/pedidos-service.yaml
kubectl apply -f k8s/pedidos-servicemonitor.yaml
kubectl apply -f k8s/prometheus-rules.yaml

Write-Host "Esperando a que los microservicios esten listos..." -ForegroundColor Cyan
kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=120s
kubectl rollout status deployment/pedidos-deployment -n bodega-peirano --timeout=120s

# 7. Informacion de acceso
$minikubeIp = (minikube ip).Trim()

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "      DESPLIEGUE COMPLETADO EXITOSAMENTE -- BODEGA PEIRANO       " -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host "Minikube IP: $minikubeIp`n" -ForegroundColor Cyan
Write-Host "Servicio A (Catalogo):  http://${minikubeIp}:30081/api/vinos" -ForegroundColor Yellow
Write-Host "   Health Check:        http://${minikubeIp}:30081/actuator/health"
Write-Host "   Metricas:            http://${minikubeIp}:30081/metrics"
Write-Host "Servicio B (Pedidos):   http://${minikubeIp}:30082/api/pedidos" -ForegroundColor Yellow
Write-Host "   Health Check:        http://${minikubeIp}:30082/actuator/health"
Write-Host "   Metricas:            http://${minikubeIp}:30082/metrics`n"
Write-Host "Grafana:                http://${minikubeIp}:31000" -ForegroundColor Yellow
Write-Host "   Usuario:             admin" -ForegroundColor Cyan
Write-Host "   Password:            bodega-peirano-2026" -ForegroundColor Cyan
Write-Host "Prometheus:             http://${minikubeIp}:30090`n" -ForegroundColor Yellow
Write-Host "Para importar el dashboard: grafana/bodega-peirano-dashboard.json" -ForegroundColor Cyan
Write-Host "Para generar pruebas de carga: .\scripts\load-testing\stress.ps1" -ForegroundColor Cyan
