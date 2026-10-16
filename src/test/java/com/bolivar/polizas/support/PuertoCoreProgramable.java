package com.bolivar.polizas.support;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Doble del puerto del CORE para pruebas: devuelve los resultados encolados (o confirma si no
 * hay ninguno) y registra cada solicitud recibida.
 */
public class PuertoCoreProgramable implements CoreMutationPort {

    private final Deque<ResultadoEnvio> respuestas = new ArrayDeque<>();
    private final List<SolicitudCore> recibidas = new CopyOnWriteArrayList<>();
    private volatile ResultadoEnvio porDefecto;

    public PuertoCoreProgramable() {
        reiniciar();
    }

    public synchronized void encolar(ResultadoEnvio... resultados) {
        respuestas.addAll(List.of(resultados));
    }

    public void siempre(ResultadoEnvio resultado) {
        porDefecto = resultado;
    }

    public synchronized void reiniciar() {
        respuestas.clear();
        recibidas.clear();
        porDefecto = new ResultadoEnvio.Confirmado(200, "{\"simulado\":true}", AdaptadorIntegracion.SIMULADO);
    }

    public List<SolicitudCore> recibidas() {
        return recibidas;
    }

    @Override
    public synchronized ResultadoEnvio enviar(SolicitudCore solicitud) {
        recibidas.add(solicitud);
        ResultadoEnvio siguiente = respuestas.poll();
        return siguiente != null ? siguiente : porDefecto;
    }

    @Override
    public AdaptadorIntegracion tipo() {
        return AdaptadorIntegracion.SIMULADO;
    }
}
