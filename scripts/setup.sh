#!/usr/bin/env bash
# ==============================================================================
# Script de Despliegue Automatizado - Bodega Peirano (Kubernetes + Prometheus + Grafana)
# ==============================================================================
set -euo pipefail

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}   INICIANDO SETUP: BODEGA PEIRANO MICROSERVICIOS KUBERNETES    ${NC}"
echo -e "${CYAN}================================================================${NC}"

# 1. Verificación de herramientas requeridas
echo -e "\n${YELLOW}[1/7] Verificando herramientas locales...${NC}"
for cmd in docker minikube kubectl helm mvn; do
    if ! command -v "$cmd" &> /dev/null; then
        echo -e "${RED}[ERROR] Herramienta requerida no encontrada: $cmd${NC}"
        exit 1
    fi
    echo -e "  ✔ $cmd: $(command -v "$cmd")"
done

# 2. Iniciar Minikube si no está en ejecución
echo -e "\n${YELLOW}[2/7] Verificando estado de Minikube...${NC}"
if ! minikube status | grep -q "Running"; then
    echo -e "Iniciando Minikube con driver docker..."
    minikube start --driver=docker --cpus=2 --memory=4096
else
    echo -e "${GREEN}Minikube ya está en ejecución.${NC}"
fi

# 3. Habilitar addon metrics-server
echo -e "\n${YELLOW}[3/7] Habilitando addon metrics-server en Minikube...${NC}"
minikube addons enable metrics-server

# 4. Instalar kube-prometheus-stack mediante Helm
echo -e "\n${YELLOW}[4/7] Configurando Stack de Monitoreo Prometheus & Grafana...${NC}"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts || true
helm repo update

echo -e "Instalando / Actualizando kube-prometheus-stack con valores personalizados..."
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    --create-namespace \
    -f k8s/values-prometheus.yaml

# 5. Compilar y construir imágenes Docker en el entorno Minikube
echo -e "\n${YELLOW}[5/7] Configurando Docker daemon de Minikube y construyendo imágenes...${NC}"
eval $(minikube docker-env)

echo -e "Construyendo imagen: catalogo-service:latest..."
docker build -t catalogo-service:latest catalogo-service/

echo -e "Construyendo imagen: pedidos-service:latest..."
docker build -t pedidos-service:latest pedidos-service/

# 6. Desplegar manifiestos de Kubernetes
echo -e "\n${YELLOW}[6/7] Aplicando manifiestos en namespace bodega-peirano...${NC}"
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/catalogo-deployment.yaml
kubectl apply -f k8s/catalogo-service.yaml
kubectl apply -f k8s/catalogo-servicemonitor.yaml
kubectl apply -f k8s/pedidos-deployment.yaml
kubectl apply -f k8s/pedidos-service.yaml
kubectl apply -f k8s/pedidos-servicemonitor.yaml
kubectl apply -f k8s/prometheus-rules.yaml

echo -e "Esperando a que los microservicios estén listos..."
kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=120s
kubectl rollout status deployment/pedidos-deployment -n bodega-peirano --timeout=120s

# 7. Información de acceso
MINIKUBE_IP=$(minikube ip)

echo -e "\n${GREEN}================================================================${NC}"
echo -e "${GREEN}      ¡DESPLIEGUE COMPLETADO EXITOSAMENTE — BODEGA PEIRANO!      ${NC}"
echo -e "${GREEN}================================================================${NC}"
echo -e "Minikube IP: ${CYAN}${MINIKUBE_IP}${NC}\n"
echo -e "📍 ${YELLOW}Servicio A (Catálogo):${NC}  http://${MINIKUBE_IP}:30081/api/vinos"
echo -e "   Health Check:             http://${MINIKUBE_IP}:30081/actuator/health"
echo -e "   Métricas:                 http://${MINIKUBE_IP}:30081/metrics"
echo -e "📍 ${YELLOW}Servicio B (Pedidos):${NC}   http://${MINIKUBE_IP}:30082/api/pedidos"
echo -e "   Health Check:             http://${MINIKUBE_IP}:30082/actuator/health"
echo -e "   Métricas:                 http://${MINIKUBE_IP}:30082/metrics\n"
echo -e "📊 ${YELLOW}Grafana:${NC}                http://${MINIKUBE_IP}:31000"
echo -e "   Usuario:                  ${CYAN}admin${NC}"
echo -e "   Contraseña:               ${CYAN}bodega-peirano-2026${NC}\n"
echo -e "📈 ${YELLOW}Prometheus:${NC}             http://${MINIKUBE_IP}:30090\n"
echo -e "Para importar el dashboard en Grafana: use el archivo ${CYAN}grafana/bodega-peirano-dashboard.json${NC}"
echo -e "Para generar carga y validar casuísticas: ejecute ${CYAN}./scripts/load-testing/stress.sh${NC}"
