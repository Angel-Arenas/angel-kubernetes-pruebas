package com.bodegapeirano.catalogo.model;

public class Vino {
    private String id;
    private String nombre;
    private String bodega;
    private String categoria;
    private String varietal;
    private int cosecha;
    private double precio;
    private int stock;
    private String descripcion;

    public Vino() {
    }

    public Vino(String id, String nombre, String bodega, String categoria, String varietal, int cosecha, double precio, int stock, String descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.bodega = bodega;
        this.categoria = categoria;
        this.varietal = varietal;
        this.cosecha = cosecha;
        this.precio = precio;
        this.stock = stock;
        this.descripcion = descripcion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getBodega() {
        return bodega;
    }

    public void setBodega(String bodega) {
        this.bodega = bodega;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getVarietal() {
        return varietal;
    }

    public void setVarietal(String varietal) {
        this.varietal = varietal;
    }

    public int getCosecha() {
        return cosecha;
    }

    public void setCosecha(int cosecha) {
        this.cosecha = cosecha;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
