package pe.sevens.sigro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.dominio.CompraCotejable
import pe.sevens.sigro.dominio.CotejoQr
import pe.sevens.sigro.dominio.QrFactura
import pe.sevens.sigro.dominio.ResultadoCotejo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HU-73 · Los casos del contrato, leídos del MISMO archivo que usa el backend (docs/contratos/hu-73-qr-casos.json).
 * Si alguien cambia la regla en un lado y no en el otro, esta prueba o la de JUnit falla.
 */
class QrFacturaContratoTest {
    private val casos: JsonObject by lazy {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/contratos/hu-73-qr-casos.json") }.first { it.exists() }
        Json.parseToJsonElement(f.readText()).jsonObject
    }
    private fun JsonElement.texto(k: String) = jsonObject[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    @Test
    fun losQrValidosSeLeenIgualQueEnElServidor() {
        casos["validos"]!!.jsonArray.forEach { c ->
            val nombre = c.texto("nombre")
            val q = assertNotNull(QrFactura.leer(c.texto("qr")), "debió leerse: $nombre")
            val e = c.jsonObject["esperado"]!!
            assertEquals(e.texto("ruc_emisor"), q.rucEmisor, nombre)
            assertEquals(e.texto("tipo_comprobante"), q.tipoComprobante, nombre)
            assertEquals(e.texto("numero_comprobante"), q.numeroComprobante, nombre)
            assertEquals(e.texto("igv"), q.igv, nombre)
            assertEquals(e.texto("total"), q.total, nombre)
            assertEquals(e.texto("fecha"), q.fecha, nombre)
            assertEquals(e.texto("adquirente_tipo"), q.adquirenteTipo, nombre)
            assertEquals(e.texto("adquirente_documento"), q.adquirenteDocumento, nombre)
        }
    }

    @Test
    fun losQrNoReconocidosNoSeAdivinan() {
        casos["no_reconocidos"]!!.jsonArray.forEach { c -> assertNull(QrFactura.leer(c.texto("qr")), "no debió leerse: ${c.texto("nombre")}") }
    }

    @Test
    fun elCotejoDaLosMismosSeisResultadosQueElServidor() {
        val validos = casos["validos"]!!.jsonArray
        val vistos = mutableSetOf<ResultadoCotejo>()
        casos["cotejo"]!!.jsonArray.forEach { c ->
            val indice = c.texto("qr")!!.substringAfter('[').substringBefore(']').toInt()
            val qr = QrFactura.leer(validos[indice].texto("qr"))!!
            fun compra(k: JsonElement) = CompraCotejable(k.jsonObject["id"]?.jsonPrimitive?.content?.toInt(), k.texto("proveedor_ruc"),
                k.texto("tipo_comprobante")!!, k.texto("numero_comprobante")!!, k.texto("total"), k.texto("igv"), k.texto("estado")!!,
                k.jsonObject["queda_por_recibir"]!!.jsonPrimitive.boolean, k.texto("fecha"), k.texto("moneda") ?: "PEN", k.texto("motivo_anulacion"))
            val compras = c.jsonObject["compras"]?.jsonArray?.map(::compra)
                ?: c.jsonObject["compra"]?.takeIf { it !is JsonNull }?.let { listOf(compra(it)) } ?: emptyList()
            val r = CotejoQr.cotejar(qr, compras, casos.texto("ruc_sevens")!!)
            val nombre = c.texto("nombre")
            assertEquals(c.texto("resultado"), r.resultado.codigo, nombre)
            c.jsonObject["avisos"]?.jsonArray?.let { esperados -> assertEquals(esperados.map { it.jsonPrimitive.content }, r.avisos, nombre) }
            c.jsonObject["compra_id"]?.let { assertEquals(it.jsonPrimitive.content.toInt(), r.compraId, nombre) }
            if (r.resultado == ResultadoCotejo.RECIBIR_CON_AVISOS) assertTrue(r.avisos.isNotEmpty())
            vistos += r.resultado
        }
        assertEquals(ResultadoCotejo.entries.toSet(), vistos, "el JSON cubre los seis resultados")
    }

    @Test
    fun elAvisoDiceLaDiferenciaEnSoles() {
        val qr = QrFactura.leer("20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|")!!
        val c = CompraCotejable(7, "20999999990", "factura", "F001-1234", "1062.00", "180.00", "registrada", true)
        assertEquals(listOf("El total del QR (S/ 1 180.00) difiere en S/ 118.00 del registrado en la compra (S/ 1 062.00)."), CotejoQr.cotejar(qr, listOf(c)).avisos)
    }

    @Test
    fun elNumeroGuardadoConCerosTambienCoincide() {
        assertEquals("F001-1234", CotejoQr.normalizarNumero("f001-00001234"))
        assertEquals("F001-0", CotejoQr.normalizarNumero("F001-0000"))
    }
}
