package com.bodegapeirano.pedidos.model;

import java.time.LocalDateTime;

public class Pedido {
    private String id;
    private String cliente;
    private String vinoId;
    private String nombreVino;
    private int cantidad;
    private double precioUnitario;
    private double montoTotal;
    private String estado; // APROBADO, RECHAZADO, FALLIDO
    private String motivo;
    private LocalDateTime fechaHora;

    public Pedido() {
        this.fechaHora = LocalDateTime.now();
    }

    public Pedido(String id, String cliente, String vinoId, String nombreVino, int cantidad, double precioUnitario, double montoTotal, String estado, String motivo) {
        this.id = id;
        this.cliente = cliente;
        this.vinoId = vinoId;
        this.nombreVino = nombreVino;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.montoTotal = montoTotal;
        this.estado = estado;
        this.motivo = motivo;
        this.fechaHora = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public String getVinoId() {
        return vinoId;
    }

    public void setVinoId(String vinoId) {
        this.vinoId = vinoId;
    }

    public String getNombreVino() {
        return nombreVino;
    }

    public void setNombreVino(String nombreVino) {
        this.nombreVino = nombreVino;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public double getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(double precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public double getMontoTotal() {
        return montoTotal;
    }

    public void setMontoTotal(double montoTotal) {
        this.montoTotal = montoTotal;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
