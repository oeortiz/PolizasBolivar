package com.bolivar.polizas.entity;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.PoliticaMonetaria;
import com.bolivar.polizas.domain.Vigencia;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;
import com.bolivar.polizas.entity.enums.TipoPoliza;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.exception.ValorInvalidoException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Póliza de arrendamiento y raíz del agregado Póliza–Riesgos.
 *
 * <p>Toda modificación de riesgos pasa por esta clase, que recalcula canon y prima
 * en el mismo método. Así nunca quedan valores incoherentes entre póliza y riesgos.</p>
 *
 * <ul>
 *   <li>INDIVIDUAL: canon y prima de la póliza son los de su único riesgo. No se suman
 *       la prima de la póliza y la del riesgo (sería doble conteo).</li>
 *   <li>COLECTIVA: prima = Σ primas de riesgos ACTIVO; canon = Σ cánones de riesgos
 *       ACTIVO (supuesto S-03).</li>
 * </ul>
 *
 * Canon y prima nunca se reciben desde fuera: se derivan de los riesgos con
 * {@link CalculadoraPrima}. No existe borrado físico.
 */
@Entity
@Table(name = "poliza")
public class Poliza extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false, updatable = false)
    private TipoPoliza tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPoliza estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_sincronizacion", length = 20, nullable = false)
    private EstadoSincronizacion estadoSincronizacion;

    @Column(name = "canon_mensual", precision = 15, scale = 2, nullable = false)
    private BigDecimal canonMensual;

    @Column(name = "prima", precision = 17, scale = 2, nullable = false)
    private BigDecimal prima;

    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Column(name = "fecha_fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    /** Duración inicial en meses; se reutiliza en cada renovación. */
    @Column(name = "duracion_meses", nullable = false, updatable = false)
    private int duracionMeses;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tipoDocumento",   column = @Column(name = "tomador_tipo_documento")),
            @AttributeOverride(name = "numeroDocumento", column = @Column(name = "tomador_numero_documento")),
            @AttributeOverride(name = "nombre",          column = @Column(name = "tomador_nombre"))
    })
    private Participante tomador;

    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    @Column(name = "motivo_cancelacion", length = 250)
    private String motivoCancelacion;

    /** Sin cascada de borrado ni orphanRemoval: los riesgos se conservan siempre. */
    @OneToMany(mappedBy = "poliza", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<Riesgo> riesgos = new ArrayList<>();

    protected Poliza() {
        // Requerido por JPA
    }

    private Poliza(TipoPoliza tipo, Participante tomador, Vigencia vigencia) {
        this.tipo = Objects.requireNonNull(tipo, "tipo es obligatorio");
        if (tomador == null) {
            throw new ValorInvalidoException("El tomador es obligatorio");
        }
        if (vigencia == null) {
            throw new ValorInvalidoException("La vigencia es obligatoria");
        }
        this.tomador = tomador;
        this.fechaInicioVigencia = vigencia.fechaInicio();
        this.fechaFinVigencia = vigencia.fechaFin();
        this.duracionMeses = vigencia.duracionMeses();
        this.estado = EstadoPoliza.ACTIVA;
        this.estadoSincronizacion = EstadoSincronizacion.NO_REQUERIDA;
        this.canonMensual = PoliticaMonetaria.CERO;
        this.prima = PoliticaMonetaria.CERO;
    }

    // ------------------------------------------------------------------
    // Creación
    // ------------------------------------------------------------------

    /**
     * Crea una póliza INDIVIDUAL con exactamente un riesgo. El tomador (y asegurado)
     * es el arrendatario del riesgo; el beneficiario es el arrendador (supuesto S-04).
     */
    public static Poliza crearIndividual(DatosRiesgo riesgo, Vigencia vigencia, CalculadoraPrima calculadora) {
        if (riesgo == null) {
            throw new ValorInvalidoException("La póliza individual requiere exactamente un riesgo");
        }
        Poliza poliza = new Poliza(TipoPoliza.INDIVIDUAL, riesgo.arrendatario(), vigencia);
        poliza.agregarRiesgo(riesgo, calculadora);
        return poliza;
    }

    /** Crea una póliza COLECTIVA con uno o varios riesgos y su prima consolidada. */
    public static Poliza crearColectiva(Participante tomador,
                                        Vigencia vigencia,
                                        List<DatosRiesgo> riesgos,
                                        CalculadoraPrima calculadora) {
        if (riesgos == null || riesgos.isEmpty()) {
            throw new ValorInvalidoException("La póliza colectiva requiere al menos un riesgo");
        }
        Poliza poliza = new Poliza(TipoPoliza.COLECTIVA, tomador, vigencia);
        riesgos.forEach(datos -> poliza.agregarRiesgo(datos, calculadora));
        return poliza;
    }

    // ------------------------------------------------------------------
    // Reglas de riesgos, canon y prima
    // ------------------------------------------------------------------

    /**
     * Agrega un riesgo calculando su prima con la vigencia de la póliza y actualiza la
     * consolidación. En una INDIVIDUAL solo se admite el riesgo inicial.
     */
    public Riesgo agregarRiesgo(DatosRiesgo datos, CalculadoraPrima calculadora) {
        Objects.requireNonNull(calculadora, "calculadora es obligatoria");
        verificarNoCancelada();
        if (datos == null) {
            throw new ValorInvalidoException("Los datos del riesgo son obligatorios");
        }
        if (tipo == TipoPoliza.INDIVIDUAL && !riesgos.isEmpty()) {
            throw new ReglaNegocioException(CodigoError.RIESGO_ADICIONAL_EN_INDIVIDUAL,
                    "Una póliza INDIVIDUAL no admite riesgos adicionales");
        }
        BigDecimal canon = calculadora.validarCanon(datos.canonMensual());
        BigDecimal primaRiesgo = calculadora.calcularPrima(canon, duracionMeses);

        Riesgo riesgo = new Riesgo(this, datos.direccionInmueble(), datos.arrendatario(),
                datos.arrendador(), canon, primaRiesgo);
        riesgos.add(riesgo);
        consolidar(calculadora);
        return riesgo;
    }

    /**
     * Cancela un riesgo de una póliza COLECTIVA y actualiza la consolidación.
     *
     * <p>Decisiones provisionales (supuesto S-06, pendientes de confirmación):</p>
     * <ul>
     *   <li>El riesgo de una INDIVIDUAL no se cancela por separado: se cancela con la póliza.</li>
     *   <li>No se cancela el último riesgo activo de una COLECTIVA: debe cancelarse la póliza.</li>
     * </ul>
     */
    public void cancelarRiesgo(Riesgo riesgo, Instant fecha, String motivo, CalculadoraPrima calculadora) {
        Objects.requireNonNull(calculadora, "calculadora es obligatoria");
        verificarNoCancelada();
        if (riesgo == null || !riesgos.contains(riesgo)) {
            throw new ReglaNegocioException(CodigoError.RIESGO_NO_PERTENECE_A_POLIZA,
                    "El riesgo no pertenece a la póliza");
        }
        if (!riesgo.isActivo()) {
            throw new ReglaNegocioException(CodigoError.RIESGO_YA_CANCELADO, "El riesgo ya está cancelado");
        }
        if (tipo == TipoPoliza.INDIVIDUAL) {
            throw new ReglaNegocioException(CodigoError.RIESGO_INDIVIDUAL_NO_CANCELABLE,
                    "El riesgo de una póliza INDIVIDUAL se cancela cancelando la póliza");
        }
        if (riesgosActivos().size() == 1) {
            throw new ReglaNegocioException(CodigoError.ULTIMO_RIESGO_ACTIVO,
                    "No se puede cancelar el último riesgo activo; cancele la póliza");
        }
        riesgo.cancelar(fecha, motivo);
        consolidar(calculadora);
    }

    /**
     * Recalcula la prima de cada riesgo activo (canon × duración) y la consolidación.
     * Se usa cuando cambian los cánones, por ejemplo al aplicar IPC (Etapa 4).
     */
    public void recalcularPrimas(CalculadoraPrima calculadora) {
        Objects.requireNonNull(calculadora, "calculadora es obligatoria");
        for (Riesgo riesgo : riesgosActivos()) {
            riesgo.asignarPrima(calculadora.calcularPrima(riesgo.getCanonMensual(), duracionMeses));
        }
        consolidar(calculadora);
    }

    /**
     * Cancela la póliza (sin borrado físico). Los riesgos activos se cancelan con ella porque
     * no tienen vigencia propia (supuesto S-16). Canon y prima conservan los últimos valores
     * vigentes como referencia histórica; no se recalculan a cero.
     */
    public void cancelar(Instant fecha, String motivo) {
        verificarNoCancelada();
        if (fecha == null) {
            throw new ValorInvalidoException("La fecha de cancelación es obligatoria");
        }
        for (Riesgo riesgo : riesgosActivos()) {
            riesgo.cancelar(fecha, motivo);
        }
        this.estado = EstadoPoliza.CANCELADA;
        this.fechaCancelacion = fecha;
        this.motivoCancelacion = motivo;
    }

    /**
     * Renueva la póliza aplicando el IPC.
     *
     * <ol>
     *   <li>Solo ACTIVA o RENOVADA; una CANCELADA se rechaza.</li>
     *   <li>CANON NUEVO = CANON ANTERIOR × (1 + IPC/100), por cada riesgo ACTIVO (los cancelados
     *       no se renuevan). En una COLECTIVA el canon consolidado es la suma de los cánones nuevos
     *       ya redondeados, no el IPC aplicado sobre la suma.</li>
     *   <li>PRIMA NUEVA = CANON NUEVO × duración inicial en meses; se reconsolida.</li>
     *   <li>La nueva vigencia empieza en la fecha fin anterior con la misma duración (S-02).</li>
     *   <li>El estado pasa a RENOVADA (S-07); la confirmación del CORE se refleja en
     *       {@code estadoSincronizacion}, no en el estado contractual.</li>
     * </ol>
     * El IPC llega ya validado por el servicio de IPC; aquí no se asume ningún valor por defecto.
     */
    /**
     * Valida que el estado permita renovar (solo ACTIVA o RENOVADA). Se invoca antes de
     * consultar el IPC para que el error refleje la causa real.
     */
    public void verificarPermiteRenovar() {
        verificarNoCancelada();
    }

    public ResultadoRenovacion renovar(BigDecimal ipcPorcentaje, CalculadoraPrima calculadora) {
        Objects.requireNonNull(calculadora, "calculadora es obligatoria");
        verificarNoCancelada();
        BigDecimal ipc = calculadora.validarIpc(ipcPorcentaje);

        FotografiaPoliza anterior = FotografiaPoliza.de(this);
        List<Riesgo> activos = riesgosActivos();
        List<BigDecimal[]> previos = activos.stream()
                .map(r -> new BigDecimal[]{r.getCanonMensual(), r.getPrima()})
                .toList();

        // Se calculan y validan todos los valores antes de modificar nada: si un riesgo
        // desborda los límites, el agregado queda intacto.
        List<BigDecimal> nuevosCanones = activos.stream()
                .map(r -> calculadora.aplicarIpc(r.getCanonMensual(), ipc))
                .toList();
        nuevosCanones.forEach(canon -> calculadora.calcularPrima(canon, duracionMeses));
        Vigencia nuevaVigencia = getVigencia().siguiente();

        for (int i = 0; i < activos.size(); i++) {
            activos.get(i).actualizarCanon(nuevosCanones.get(i));
        }
        recalcularPrimas(calculadora);

        this.fechaInicioVigencia = nuevaVigencia.fechaInicio();
        this.fechaFinVigencia = nuevaVigencia.fechaFin();
        this.estado = EstadoPoliza.RENOVADA;

        List<RenovacionRiesgo> detalle = new ArrayList<>();
        for (int i = 0; i < activos.size(); i++) {
            Riesgo r = activos.get(i);
            detalle.add(new RenovacionRiesgo(r.getId(), previos.get(i)[0], r.getCanonMensual(),
                    previos.get(i)[1], r.getPrima()));
        }
        return new ResultadoRenovacion(anterior, FotografiaPoliza.de(this), detalle);
    }

    /** La última mutación local está registrada para el CORE y aún no está confirmada. */
    public void marcarSincronizacionPendiente() {
        this.estadoSincronizacion = EstadoSincronizacion.PENDIENTE;
    }

    /** Estado técnico derivado de las operaciones de integración (lo calcula el procesador). */
    public void actualizarEstadoSincronizacion(EstadoSincronizacion nuevo) {
        this.estadoSincronizacion = Objects.requireNonNull(nuevo);
    }

    /** Riesgos ACTIVO en orden de alta. */
    public List<Riesgo> riesgosActivos() {
        return riesgos.stream().filter(Riesgo::isActivo).toList();
    }

    public Vigencia getVigencia() {
        return new Vigencia(fechaInicioVigencia, fechaFinVigencia, duracionMeses);
    }

    private void consolidar(CalculadoraPrima calculadora) {
        List<Riesgo> activos = riesgosActivos();
        if (tipo == TipoPoliza.INDIVIDUAL) {
            if (riesgos.size() != 1 || activos.size() != 1) {
                throw new IllegalStateException("Una póliza INDIVIDUAL activa debe tener exactamente un riesgo activo");
            }
            Riesgo unico = activos.getFirst();
            this.canonMensual = unico.getCanonMensual();
            this.prima = unico.getPrima();
            return;
        }
        this.canonMensual = calculadora.consolidar(activos.stream().map(Riesgo::getCanonMensual).toList());
        this.prima = calculadora.consolidar(activos.stream().map(Riesgo::getPrima).toList());
    }

    private void verificarNoCancelada() {
        if (estado == EstadoPoliza.CANCELADA) {
            throw new ReglaNegocioException(CodigoError.POLIZA_CANCELADA,
                    "La póliza está cancelada y no admite modificaciones");
        }
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public TipoPoliza getTipo() {
        return tipo;
    }

    public EstadoPoliza getEstado() {
        return estado;
    }

    public EstadoSincronizacion getEstadoSincronizacion() {
        return estadoSincronizacion;
    }

    public BigDecimal getCanonMensual() {
        return canonMensual;
    }

    public BigDecimal getPrima() {
        return prima;
    }

    public LocalDate getFechaInicioVigencia() {
        return fechaInicioVigencia;
    }

    public LocalDate getFechaFinVigencia() {
        return fechaFinVigencia;
    }

    public int getDuracionMeses() {
        return duracionMeses;
    }

    public Participante getTomador() {
        return tomador;
    }

    public Instant getFechaCancelacion() {
        return fechaCancelacion;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public List<Riesgo> getRiesgos() {
        return Collections.unmodifiableList(riesgos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Poliza other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Poliza.class.hashCode();
    }

    @Override
    public String toString() {
        return "Poliza[id=" + id + ", tipo=" + tipo + ", estado=" + estado + "]";
    }
}
