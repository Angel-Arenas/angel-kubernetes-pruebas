#!/usr/bin/env bash
# ==============================================================================
# Script de Desinstalación y Limpieza - Bodega Peirano
# ==============================================================================
set -euo pipefail

CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}   LIMPIEZA DE RECURSOS: BODEGA PEIRANO MICROSERVICIOS         ${NC}"
echo -e "${CYAN}================================================================${NC}"

DELETE_CLUSTER=false

for arg in "$@"; do
    case $arg in
        --delete-cluster|-c|--all)
            DELETE_CLUSTER=true
            shift
            ;;
    esac
done

echo -e "\n${YELLOW}[1/3] Eliminando namespace y aplicaciones bodega-peirano...${NC}"
kubectl delete namespace bodega-peirano --ignore-not-found=true

if [ "$DELETE_CLUSTER" = true ]; then
    echo -e "\n${YELLOW}[2/3] Eliminando namespace de monitoreo y liberando Helm...${NC}"
    helm uninstall prometheus -n monitoring 2>/dev/null || true
    kubectl delete namespace monitoring --ignore-not-found=true

    echo -e "\n${RED}[3/3] Eliminando cluster Minikube completo (--delete-cluster activado)...${NC}"
    minikube delete
    echo -e "\n${GREEN}✔ Cluster Minikube y todos los recursos han sido eliminados por completo.${NC}"
    exit 0
fi

if [ -t 0 ]; then
    read -p "¿Desea también desinstalar Prometheus & Grafana (namespace monitoring)? [s/N]: " RESP || RESP="n"
else
    RESP="n"
fi

if [[ "$RESP" =~ ^[sSyY]$ ]]; then
    echo -e "\n${YELLOW}[2/3] Desinstalando stack kube-prometheus-stack...${NC}"
    helm uninstall prometheus -n monitoring 2>/dev/null || true
    kubectl delete namespace monitoring --ignore-not-found=true
else
    echo -e "Omitiendo desinstalación de Prometheus & Grafana."
fi

echo -e "\n${GREEN}✔ Limpieza de microservicios completada exitosamente.${NC}"
echo -e "Tip: Para eliminar también el cluster Minikube completo use: ${CYAN}./scripts/teardown.sh --delete-cluster${NC}"
