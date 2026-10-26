param(
    [switch]$DeleteCluster
)

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   LIMPIEZA DE RECURSOS: BODEGA PEIRANO MICROSERVICIOS         " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

Write-Host "`n[1/3] Eliminando namespace y aplicaciones bodega-peirano..." -ForegroundColor Yellow
kubectl delete namespace bodega-peirano --ignore-not-found=true

if ($DeleteCluster) {
    Write-Host "`n[2/3] Desinstalando stack kube-prometheus-stack..." -ForegroundColor Yellow
    helm uninstall prometheus -n monitoring 2>$null
    kubectl delete namespace monitoring --ignore-not-found=true

    Write-Host "`n[3/3] Eliminando cluster Minikube completo (-DeleteCluster activado)..." -ForegroundColor Red
    minikube delete
    Write-Host "`n[OK] Cluster Minikube y recursos eliminados por completo." -ForegroundColor Green
    exit 0
}

$resp = Read-Host "Desea tambien desinstalar Prometheus y Grafana (namespace monitoring)? [s/N]"
if ($resp -match "^[sSyY]$") {
    Write-Host "`n[2/3] Desinstalando stack kube-prometheus-stack..." -ForegroundColor Yellow
    helm uninstall prometheus -n monitoring 2>$null
    kubectl delete namespace monitoring --ignore-not-found=true
} else {
    Write-Host "Omitiendo desinstalacion de Prometheus y Grafana." -ForegroundColor Gray
}

Write-Host "`n[OK] Limpieza de microservicios completada exitosamente." -ForegroundColor Green
Write-Host "Tip: Para eliminar tambien el cluster Minikube completo ejecute: .\scripts\teardown.ps1 -DeleteCluster" -ForegroundColor Cyan
