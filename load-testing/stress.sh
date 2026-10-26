#!/usr/bin/env bash
# ==============================================================================
# Script de Pruebas de Carga y Validación de Casuísticas - Bodega Peirano
# ==============================================================================
set -euo pipefail

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Determinar host de acceso (Minikube IP o localhost)
MINIKUBE_IP=$(minikube ip 2>/dev/null || echo "127.0.0.1")
HOST="${1:-$MINIKUBE_IP}"
CATALOGO_URL="http://${HOST}:30081"
PEDIDOS_URL="http://${HOST}:30082"

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}      BODEGA PEIRANO — GENERADOR DE CARGA Y CASUÍSTICAS         ${NC}"
echo -e "${CYAN}================================================================${NC}"
echo -e "Catálogo API: ${YELLOW}${CATALOGO_URL}${NC}"
echo -e "Pedidos API:  ${YELLOW}${PEDIDOS_URL}${NC}\n"

mostrar_menu() {
    echo -e "${YELLOW}Seleccione el escenario a ejecutar:${NC}"
    echo "1) Flujo Normal de Pedidos (Compras exitosas, rotación de stock e ingresos)"
    echo "2) Casuística 3 de Negocio: Alta Tasa de Rechazo y Stock Agotado (Regla de negocio)"
    echo "3) Casuística 2 de Servicio Lento: ARTIFICIAL_DELAY_SECONDS=5 (Timeout en B y Gauge de Latencia)"
    echo "4) Casuística 1 de Servicio Caído: CrashLoopBackOff por error de config (SERVER_PORT=-1)"
    echo "5) Restaurar Servicio Caído (SERVER_PORT=8081 -> Running)"
    echo "6) Ejecutar Suite Completa de Pruebas de Estrés (Mixta)"
    echo "7) Restaurar Servicio Lento (ARTIFICIAL_DELAY_SECONDS=0)"
    echo "0) Salir"
    read -p "Opción [0-7]: " OPCION
}

flujo_normal() {
    echo -e "\n${CYAN}>>> Generando 20 pedidos válidos variados...${NC}"
    CLIENTES=("Restaurante Astrid y Gaston" "Hotel Westin Lima" "Bodega Don Jose" "Sommelier Carlos" "Lucia Bar")
    VINOS=("VINO-001" "VINO-002" "VINO-003" "VINO-004")

    for i in {1..20}; do
        CLI=${CLIENTES[$RANDOM % ${#CLIENTES[@]}]}
        VINO=${VINOS[$RANDOM % ${#VINOS[@]}]}
        CANT=$(( (RANDOM % 3) + 1 ))

        curl -s -X POST "${PEDIDOS_URL}/api/pedidos" \
             -H "Content-Type: application/json" \
             -d "{\"cliente\":\"$CLI\",\"vinoId\":\"$VINO\",\"cantidad\":$CANT}" \
             | grep -o '"estado":"[^"]*"' || true
        sleep 0.3
    done
    echo -e "\n${GREEN}✔ Flujo normal finalizado. Revise paneles de Ingresos y Flujo en Grafana.${NC}"
}

casuistica_negocio() {
    echo -e "\n${CYAN}>>> Ejecutando Casuística de Negocio: Alta Tasa de Rechazos y Stock Agotado...${NC}"
    echo -e "Enviando pedidos con cantidades excesivas (stock insuficiente) y vinos inexistentes..."

    for i in {1..30}; do
        if (( i % 2 == 0 )); then
            # Pide 500 botellas de un vino que solo tiene stock reducido
            curl -s -X POST "${PEDIDOS_URL}/api/pedidos" \
                 -H "Content-Type: application/json" \
                 -d '{"cliente":"Supermercado Masivo","vinoId":"VINO-005","cantidad":500}' \
                 | grep -o '"estado":"[^"]*"' || true
        else
            # Pide vino inexistente
            curl -s -X POST "${PEDIDOS_URL}/api/pedidos" \
                 -H "Content-Type: application/json" \
                 -d '{"cliente":"Cliente Fantasma","vinoId":"VINO-999","cantidad":2}' \
                 | grep -o '"estado":"[^"]*"' || true
        fi
        sleep 0.2
    done
    echo -e "\n${GREEN}✔ Casuística disparada. Observe el panel 'Tasa de Rechazo (%)' y alerta 'HighOrderRejectionRate' en Prometheus.${NC}"
}

casuistica_lento() {
    echo -e "\n${CYAN}>>> Ejecutando Casuística 2: Servicio Lento (Degradación con ARTIFICIAL_DELAY_SECONDS=5)...${NC}"
    echo -e "Configurando variable de entorno ARTIFICIAL_DELAY_SECONDS=5 en catalogo-deployment..."
    echo -e "(Esto supera el timeout de 4s configurado en el Servicio B y expone el Gauge en Grafana/Prometheus)"
    kubectl set env deployment/catalogo-deployment -n bodega-peirano ARTIFICIAL_DELAY_SECONDS="5"
    kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=60s

    echo -e "\nEnviando pedidos al Servicio B (deben experimentar timeout tras 4 segundos):"
    for i in {1..3}; do
        echo -e "Pedido #$i con Servicio A demorando 5s..."
        curl -s -X POST "${PEDIDOS_URL}/api/pedidos" \
             -H "Content-Type: application/json" \
             -d '{"cliente":"Cliente Timeout","vinoId":"VINO-001","cantidad":1}' \
             | grep -o '"estado":"[^"]*"' || true
    done

    echo -e "\n${GREEN}✔ Casuística disparada. Observe el Gauge de delay (5s), latencia p95 y alerta 'SlowOrderProcessingLatency'.${NC}"
    echo -e "Para restaurar la latencia normal ejecute la opción 7 o: kubectl set env deployment/catalogo-deployment -n bodega-peirano ARTIFICIAL_DELAY_SECONDS=\"0\""
}

restaurar_lento() {
    echo -e "\n${GREEN}>>> Restaurando latencia normal en Servicio A (ARTIFICIAL_DELAY_SECONDS=0)...${NC}"
    kubectl set env deployment/catalogo-deployment -n bodega-peirano ARTIFICIAL_DELAY_SECONDS="0"
    kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=60s
    echo -e "\n${GREEN}✔ Servicio A restaurado con latencia normal. Errores y latencia regresan a valores basales.${NC}"
}

casuistica_caido() {
    echo -e "\n${RED}>>> Ejecutando Casuística 1: Servicio Caído (Simulación CrashLoopBackOff por Error de Configuración)...${NC}"
    echo -e "Inyectando configuración inválida: SERVER_PORT=-1 en catalogo-deployment..."
    kubectl set env deployment/catalogo-deployment -n bodega-peirano SERVER_PORT="-1"

    echo -e "Esperando a que Kubernetes detecte el fallo y entre en CrashLoopBackOff..."
    sleep 8
    kubectl get pods -n bodega-peirano -l app=catalogo-service

    echo -e "\nEnviando pedidos al Servicio B mientras Catálogo está en CrashLoopBackOff:"
    for i in {1..4}; do
        curl -s -X POST "${PEDIDOS_URL}/api/pedidos" \
             -H "Content-Type: application/json" \
             -d '{"cliente":"Cliente Intento","vinoId":"VINO-001","cantidad":1}' \
             | grep -o '"estado":"[^"]*"' || true
        sleep 0.5
    done

    echo -e "\n${YELLOW}✔ Verifique en Grafana que el panel 'Disponibilidad' marca DOWN en rojo y se activa la alerta 'CatalogoServiceDown'.${NC}"
    echo -e "En Kubernetes: ejecute 'kubectl get pods -n bodega-peirano' para observar el estado CrashLoopBackOff."
    echo -e "Para restaurar el servicio seleccione la opción 5 en el menú."
}

restaurar_servicio() {
    echo -e "\n${GREEN}>>> Restaurando configuración válida en Servicio A (SERVER_PORT=8081)...${NC}"
    kubectl set env deployment/catalogo-deployment -n bodega-peirano SERVER_PORT="8081"
    kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=60s
    echo -e "\n${GREEN}✔ Servicio restaurado exitosamente. Pod en Running y panel ONLINE verde en Grafana.${NC}"
}

suite_completa() {
    flujo_normal
    casuistica_negocio
    casuistica_lento
}

if [ $# -gt 0 ] && [ "$1" == "--all" ]; then
    suite_completa
    exit 0
fi

while true; do
    mostrar_menu
    case "$OPCION" in
        1) flujo_normal ;;
        2) casuistica_negocio ;;
        3) casuistica_lento ;;
        4) casuistica_caido ;;
        5) restaurar_servicio ;;
        6) suite_completa ;;
        7) restaurar_lento ;;
        0) exit 0 ;;
        *) echo -e "${RED}Opción no válida.${NC}" ;;
    esac
    echo ""
done
