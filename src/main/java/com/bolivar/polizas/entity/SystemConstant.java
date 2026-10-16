package com.bolivar.polizas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Constante de configuración (p. ej. {@code ipc_enero = 5.60}).
 * El valor se guarda como texto; la conversión y validación numérica del IPC
 * se realiza en el servicio de IPC (Etapa 4), que falla de forma controlada si
 * la constante no existe o no es numérica.
 */
@Entity
@Table(name = "system_constant")
public class SystemConstant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 100, nullable = false, unique = true, updatable = false)
    private String nombre;

    @Column(name = "valor", length = 255, nullable = false)
    private String valor;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    protected SystemConstant() {
        // Requerido por JPA
    }

    public SystemConstant(String nombre, String valor, String descripcion) {
        this.nombre = Objects.requireNonNull(nombre, "nombre es obligatorio");
        this.valor = Objects.requireNonNull(valor, "valor es obligatorio");
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getValor() {
        return valor;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemConstant other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return SystemConstant.class.hashCode();
    }

    @Override
    public String toString() {
        return "SystemConstant[" + nombre + "=" + valor + "]";
    }
}
