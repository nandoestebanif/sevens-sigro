package pe.sevens.sigro

import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.Credenciales
import pe.sevens.sigro.plataforma.AlmacenSeguro

/** Doble del almacén cifrado (Keystore / Keychain): un mapa en memoria. */
class AlmacenSeguroEnMemoria(inicial: Map<String, String> = emptyMap()) : AlmacenSeguro {
    val m = inicial.toMutableMap()
    override suspend fun leer(clave: String) = m[clave]
    override suspend fun guardar(clave: String, valor: String) { m[clave] = valor }
    override suspend fun borrar(clave: String) { m.remove(clave) }
}

/** Credenciales de prueba sobre los ajustes dados; el token, si los ajustes lo traen, se migra solo. */
fun credencialesDePrueba(ajustes: AjusteDao, seguro: AlmacenSeguro = AlmacenSeguroEnMemoria()) =
    Credenciales(ajustes, BovedaSesion(seguro, ajustes))

/** Tabla `ajuste` en memoria. */
class AjustesDePrueba(inicial: Map<String, String> = mapOf("servidor" to "http://api.prueba")) : pe.sevens.sigro.datos.local.AjusteDao {
    val m = kotlinx.coroutines.flow.MutableStateFlow(inicial)
    override suspend fun guardar(a: pe.sevens.sigro.datos.local.AjusteEntity) { m.value = m.value + (a.clave to a.valor) }
    override suspend fun leer(clave: String) = m.value[clave]
    override fun observar() = kotlinx.coroutines.flow.flow {
        m.collect { mapa -> emit(mapa.map { (k, v) -> pe.sevens.sigro.datos.local.AjusteEntity(k, v) }) }
    }
    override suspend fun quitar(claves: List<String>) { m.value = m.value - claves.toSet() }
}

/** Cola de salidas en memoria. */
class SalidasDePrueba : pe.sevens.sigro.datos.local.SalidaDao {
    val filas = kotlinx.coroutines.flow.MutableStateFlow<List<pe.sevens.sigro.datos.local.SalidaLocalEntity>>(emptyList())
    override suspend fun insertar(s: pe.sevens.sigro.datos.local.SalidaLocalEntity) { filas.value = filas.value + s }
    override fun observarDeObra(obraId: Int) = kotlinx.coroutines.flow.flow { filas.collect { l -> emit(l.filter { it.obraId == obraId }) } }
    override suspend fun pendientes() = filas.value.filter { it.estado == "PENDIENTE" }.sortedBy { it.horaDispositivoMs }
    override fun contarPendientes() = kotlinx.coroutines.flow.flow { filas.collect { l -> emit(l.count { it.estado == "PENDIENTE" }) } }
    override suspend fun marcar(uuid: String, estado: String, motivoRechazo: String?, nota: String?, ahora: Long) {
        filas.value = filas.value.map {
            if (it.uuidCliente == uuid) it.copy(estado = estado, motivoRechazo = motivoRechazo, nota = nota, intentos = it.intentos + 1, ultimoIntentoMs = ahora) else it
        }
    }
    override suspend fun anotarRecibo(uuid: String, porId: Int?, por: String?, fecha: String?) {
        filas.value = filas.value.map { if (it.uuidCliente == uuid) it.copy(registradoPorId = porId, registradoPor = por, fechaServidor = fecha) else it }
    }
    fun una(uuid: String) = filas.value.first { it.uuidCliente == uuid }
}

/** Cola general en memoria. */
class RegistrosDePrueba : pe.sevens.sigro.datos.local.RegistroDao {
    val filas = kotlinx.coroutines.flow.MutableStateFlow<List<pe.sevens.sigro.datos.local.RegistroLocalEntity>>(emptyList())
    override suspend fun insertar(r: pe.sevens.sigro.datos.local.RegistroLocalEntity) { filas.value = filas.value + r }
    override fun observarDeObra(obraId: Int) = kotlinx.coroutines.flow.flow { filas.collect { l -> emit(l.filter { it.obraId == obraId }) } }
    override suspend fun pendientes(tipos: List<String>) =
        filas.value.filter { it.estado == "PENDIENTE" && it.tipo in tipos }.sortedBy { it.horaDispositivoMs }
    override fun contarPendientes(tipos: List<String>) =
        kotlinx.coroutines.flow.flow { filas.collect { l -> emit(l.count { it.estado == "PENDIENTE" && it.tipo in tipos }) } }
    override suspend fun clavesVigentes(obraId: Int, prefijo: String) =
        filas.value.filter { it.obraId == obraId && it.clave?.startsWith(prefijo) == true && it.estado != "RECHAZADO" }.mapNotNull { it.clave }
    override suspend fun marcar(uuid: String, estado: String, motivoRechazo: String?, nota: String?, ahora: Long) {
        filas.value = filas.value.map {
            if (it.uuidCliente == uuid) it.copy(estado = estado, motivoRechazo = motivoRechazo, nota = nota, intentos = it.intentos + 1, ultimoIntentoMs = ahora) else it
        }
    }
    override suspend fun anotarRecibo(uuid: String, porId: Int?, por: String?, fecha: String?) {
        filas.value = filas.value.map { if (it.uuidCliente == uuid) it.copy(registradoPorId = porId, registradoPor = por, fechaServidor = fecha) else it }
    }
    fun una(uuid: String) = filas.value.first { it.uuidCliente == uuid }
}

/** Copia del stock en memoria. */
class StockGuardadoDePrueba : pe.sevens.sigro.datos.local.StockDao {
    val m = mutableMapOf<Int, pe.sevens.sigro.datos.local.StockGuardadoEntity>()
    override suspend fun guardar(s: pe.sevens.sigro.datos.local.StockGuardadoEntity) { m[s.obraId] = s }
    override suspend fun leer(obraId: Int) = m[obraId]
}
