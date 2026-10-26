package com.bodegapeirano.pedidos.model;

public class PedidoRequest {
    private String cliente;
    private String vinoId;
    private int cantidad;

    public PedidoRequest() {
    }

    public PedidoRequest(String cliente, String vinoId, int cantidad) {
        this.cliente = cliente;
        this.vinoId = vinoId;
        this.cantidad = cantidad;
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

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }
}
