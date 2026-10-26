# ==============================================================================
# Script de Pruebas de Carga y Validación de Casuísticas (PowerShell) - Bodega Peirano
# ==============================================================================
param (
    [string]$HostTarget = ""
)

if ([string]::IsNullOrWhiteSpace($HostTarget)) {
    try {
        $HostTarget = (minikube ip 2>$null).Trim()
    } catch {
        $HostTarget = "127.0.0.1"
    }
}
if ([string]::IsNullOrWhiteSpace($HostTarget)) {
    $HostTarget = "127.0.0.1"
}

$CatalogoUrl = "http://${HostTarget}:30081"
$PedidosUrl = "http://${HostTarget}:30082"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "      BODEGA PEIRANO — GENERADOR DE CARGA Y CASUÍSTICAS         " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Catálogo API: $CatalogoUrl" -ForegroundColor Yellow
Write-Host "Pedidos API:  $PedidosUrl`n" -ForegroundColor Yellow

function Mostrar-Menu {
    Write-Host "Seleccione el escenario a ejecutar:" -ForegroundColor Yellow
    Write-Host "1) Flujo Normal de Pedidos (Compras exitosas, rotación de stock e ingresos)"
    Write-Host "2) Casuística 3 de Negocio: Alta Tasa de Rechazo y Stock Agotado (Regla de negocio)"
    Write-Host "3) Casuística 2 de Servicio Lento: ARTIFICIAL_DELAY_SECONDS=5 (Timeout en B y Gauge de Latencia)"
    Write-Host "4) Casuística 1 de Servicio Caído: CrashLoopBackOff por error de config (SERVER_PORT=-1)"
    Write-Host "5) Restaurar Servicio Caído (SERVER_PORT=8081 -> Running)"
    Write-Host "6) Ejecutar Suite Completa de Pruebas de Estrés (Mixta)"
    Write-Host "7) Restaurar Servicio Lento (ARTIFICIAL_DELAY_SECONDS=0)"
    Write-Host "0) Salir"
    return Read-Host "Opción [0-7]"
}

function Flujo-Normal {
    Write-Host "`n>>> Generando 20 pedidos válidos variados..." -ForegroundColor Cyan
    $clientes = @("Restaurante Astrid y Gaston", "Hotel Westin Lima", "Bodega Don Jose", "Sommelier Carlos", "Lucia Bar")
    $vinos = @("VINO-001", "VINO-002", "VINO-003", "VINO-004")

    for ($i = 1; $i -le 20; $i++) {
        $cli = $clientes | Get-Random
        $vino = $vinos | Get-Random
        $cant = Get-Random -Minimum 1 -Maximum 4
        $body = @{
            cliente = $cli
            vinoId = $vino
            cantidad = $cant
        } | ConvertTo-Json

        try {
            $resp = Invoke-RestMethod -Uri "$PedidosUrl/api/pedidos" -Method Post -Body $body -ContentType "application/json"
            Write-Host "Pedido #$i: Estado = $($resp.estado) | Vino = $($resp.nombreVino) | Total = S/.$($resp.montoTotal)" -ForegroundColor Green
        } catch {
            Write-Host "Pedido #$i: Rechazado o Fallido" -ForegroundColor DarkYellow
        }
        Start-Sleep -Milliseconds 250
    }
    Write-Host "`n✔ Flujo normal finalizado. Revise paneles de Ingresos y Flujo en Grafana." -ForegroundColor Green
}

function Casuistica-Negocio {
    Write-Host "`n>>> Ejecutando Casuística 3 de Negocio: Alta Tasa de Rechazos y Stock Agotado..." -ForegroundColor Cyan
    Write-Host "Enviando pedidos con cantidades excesivas (stock insuficiente) y vinos inexistentes..." -ForegroundColor Gray

    for ($i = 1; $i -le 30; $i++) {
        if ($i % 2 -eq 0) {
            $body = @{ cliente = "Supermercado Masivo"; vinoId = "VINO-005"; cantidad = 500 } | ConvertTo-Json
        } else {
            $body = @{ cliente = "Cliente Fantasma"; vinoId = "VINO-999"; cantidad = 2 } | ConvertTo-Json
        }

        try {
            $resp = Invoke-RestMethod -Uri "$PedidosUrl/api/pedidos" -Method Post -Body $body -ContentType "application/json"
            Write-Host "Pedido #$i: $($resp.estado)" -ForegroundColor Yellow
        } catch {
            Write-Host "Pedido #$i: RECHAZADO (Stock Insuficiente / Vino No Existe)" -ForegroundColor Red
        }
        Start-Sleep -Milliseconds 200
    }
    Write-Host "`n✔ Casuística disparada. Observe el panel 'Tasa de Rechazo (%)' y alerta 'HighOrderRejectionRate' en Prometheus." -ForegroundColor Green
}

function Casuistica-Lento {
    Write-Host "`n>>> Ejecutando Casuística 2: Servicio Lento (Degradación con ARTIFICIAL_DELAY_SECONDS=5)..." -ForegroundColor Cyan
    Write-Host "Configurando variable de entorno ARTIFICIAL_DELAY_SECONDS=5 en catalogo-deployment..." -ForegroundColor Gray
    Write-Host "(Esto supera el timeout de 4s configurado en el Servicio B y expone el Gauge en Grafana/Prometheus)" -ForegroundColor DarkGray
    kubectl set env deployment/catalogo-deployment -n bodega-peirano ARTIFICIAL_DELAY_SECONDS="5"
    kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=60s

    Write-Host "`nEnviando pedidos al Servicio B (deben experimentar timeout tras 4 segundos):" -ForegroundColor Yellow
    for ($i = 1; $i -le 3; $i++) {
        $body = @{ cliente = "Cliente Timeout"; vinoId = "VINO-001"; cantidad = 1 } | ConvertTo-Json
        try {
            $resp = Invoke-RestMethod -Uri "$PedidosUrl/api/pedidos" -Method Post -Body $body -ContentType "application/json"
            Write-Host "Pedido #$i: $($resp.estado)" -ForegroundColor Yellow
        } catch {
            Write-Host "Pedido #$i: Timeout / Fallido al comunicarse con Servicio A" -ForegroundColor DarkRed
        }
    }
    Write-Host "`n✔ Casuística disparada. Observe el Gauge de delay (5s), latencia p95 y alerta 'SlowOrderProcessingLatency'." -ForegroundColor Green
    Write-Host "Para restaurar la latencia normal elija la opción 7 o: kubectl set env deployment/catalogo-deployment -n bodega-peirano ARTIFICIAL_DELAY_SECONDS=0" -ForegroundColor Cyan
}

function Restaurar-Lento {
    Write-Host "`n>>> Restaurando latencia normal en Servicio A (ARTIFICIAL_DELAY_SECONDS=0)..." -ForegroundColor Green
    kubectl set env deployment/catalogo-deployment -n bodega-peirano ARTIFICIAL_DELAY_SECONDS="0"
    kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=60s
    Write-Host "`n✔ Servicio A restaurado con latencia normal. Errores y latencia regresan a valores basales." -ForegroundColor Green
}

function Casuistica-Caido {
    Write-Host "`n>>> Ejecutando Casuística 1: Servicio Caído (Simulación CrashLoopBackOff por Error de Configuración)..." -ForegroundColor Red
    Write-Host "Inyectando configuración JVM inválida: JAVA_TOOL_OPTIONS='-XX:InvalidJvmFlag'..." -ForegroundColor Yellow
    kubectl set env deployment/catalogo-deployment -n bodega-peirano JAVA_TOOL_OPTIONS="-XX:InvalidJvmFlag"

    Write-Host "Esperando 8 segundos a que Kubernetes detecte el fallo y entre en CrashLoopBackOff..." -ForegroundColor Gray
    Start-Sleep -Seconds 8
    kubectl get pods -n bodega-peirano -l app=catalogo-service

    Write-Host "`nEnviando pedidos mientras el Servicio A está en CrashLoopBackOff:" -ForegroundColor Yellow
    for ($i = 1; $i -le 4; $i++) {
        $body = @{ cliente = "Cliente Intento"; vinoId = "VINO-001"; cantidad = 1 } | ConvertTo-Json
        try {
            $resp = Invoke-RestMethod -Uri "$PedidosUrl/api/pedidos" -Method Post -Body $body -ContentType "application/json"
            Write-Host "Pedido #$i: $($resp.estado)"
        } catch {
            Write-Host "Pedido #$i: HTTP 503 / Fallido (Servicio A inalcanzable)" -ForegroundColor DarkRed
        }
        Start-Sleep -Milliseconds 500
    }

    Write-Host "`n✔ Verifique en Grafana que el panel 'Disponibilidad' marca DOWN en rojo y se activa la alerta 'CatalogoServiceDown'." -ForegroundColor Yellow
    Write-Host "En Kubernetes: ejecute 'kubectl get pods -n bodega-peirano' para observar el estado CrashLoopBackOff." -ForegroundColor Cyan
    Write-Host "Para restaurar el servicio seleccione la opción 5 en el menú." -ForegroundColor Cyan
}

function Restaurar-Servicio {
    Write-Host "`n>>> Restaurando configuración válida en Servicio A (removiendo opción inválida)..." -ForegroundColor Green
    kubectl set env deployment/catalogo-deployment -n bodega-peirano JAVA_TOOL_OPTIONS- SERVER_PORT="8081"
    kubectl rollout status deployment/catalogo-deployment -n bodega-peirano --timeout=60s
    Write-Host "`n✔ Servicio restaurado exitosamente. Pod en Running y panel ONLINE verde en Grafana." -ForegroundColor Green
}

function Suite-Completa {
    Flujo-Normal
    Casuistica-Negocio
    Casuistica-Lento
}

do {
    $opc = Mostrar-Menu
    switch ($opc) {
        "1" { Flujo-Normal }
        "2" { Casuistica-Negocio }
        "3" { Casuistica-Lento }
        "4" { Casuistica-Caido }
        "5" { Restaurar-Servicio }
        "6" { Suite-Completa }
        "7" { Restaurar-Lento }
        "0" { Write-Host "Saliendo..."; break }
        default { Write-Host "Opción inválida." -ForegroundColor Red }
    }
    Write-Host ""
} while ($opc -ne "0")
