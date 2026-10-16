package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.TipoPoliza;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionales de GET /polizas. Un filtro nulo no restringe. */
public final class PolizaSpecifications {

    private PolizaSpecifications() {
    }

    public static Specification<Poliza> conFiltros(TipoPoliza tipo, EstadoPoliza estado) {
        return (root, query, cb) -> {
            var predicado = cb.conjunction();
            if (tipo != null) {
                predicado = cb.and(predicado, cb.equal(root.get("tipo"), tipo));
            }
            if (estado != null) {
                predicado = cb.and(predicado, cb.equal(root.get("estado"), estado));
            }
            return predicado;
        };
    }
}
