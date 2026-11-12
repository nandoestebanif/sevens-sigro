package pe.sevens.sigro.datos.local

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

/*
 * Base local del celular (Room KMP: la misma en Android e iOS, con SQLite empaquetado).
 * Guarda lo que tiene que sobrevivir a que el sistema cierre la app sin señal (ADR-02, HU-37).
 */

/** Ajustes clave → valor: servidor, datos de la sesión, obra y almacén elegidos, identificador del dispositivo.
 *  El token NO está aquí: vive cifrado en AlmacenSeguro (desde la versión 3 de la base). */
@Entity(tableName = "ajuste")
data class AjusteEntity(@PrimaryKey val clave: String, val valor: String)

/**
 * Cola de salidas (HU-37). La fila NUNCA se borra al enviarse: cambia de estado. Así el almacenero
 * ve qué envió, y un rechazo queda con su motivo (HU-38). El saldo no se guarda aquí (regla 2).
 */
@Entity(tableName = "salida_local")
data class SalidaLocalEntity(
    @PrimaryKey val uuidCliente: String,
    val obraId: Int,
    val insumoId: Int,
    val insumoCodigo: String,
    val insumoDescripcion: String,
    val unidad: String,
    val cantidad: String,
    val motivo: String,
    val quienRetira: String,
    val frente: String?,
    val horaDispositivoMs: Long,
    val estado: String,
    val motivoRechazo: String?,
    val nota: String?,
    val intentos: Int,
    val ultimoIntentoMs: Long?,
    /** M6 (versión 3): almacén de donde sale. Nulo en salidas anteriores: el servidor usa el único activo. */
    val almacenId: Int? = null,
    /** A1 (versión 4): recibo del servidor al aceptarlo (201/200): quién registró y la hora del servidor. */
    val registradoPorId: Int? = null,
    val registradoPor: String? = null,
    val fechaServidor: String? = null,
)

/** Copia del catálogo para buscar insumos en obra sin señal. Se refresca con señal. */
@Entity(tableName = "insumo_local")
data class InsumoLocalEntity(
    @PrimaryKey val id: Int,
    val codigo: String,
    val descripcion: String,
    val unidad: String,
)

/**
 * Cola general (Sprint 2): asistencia y registros de almacén. Igual que `salida_local`, la fila NUNCA se
 * borra: cambia de estado y un rechazo queda con su motivo. `cuerpo` es el JSON que viaja al servidor,
 * armado al registrar, así un reenvío manda exactamente lo mismo con el mismo uuid (regla 3).
 */
@Entity(tableName = "registro_local")
data class RegistroLocalEntity(
    @PrimaryKey val uuidCliente: String,
    val obraId: Int,
    val tipo: String,
    val titulo: String,
    val detalle: String,
    val cuerpo: String,
    /** Clave de negocio para no registrar dos veces lo mismo en el celular (jornada: trabajador + fecha). */
    val clave: String?,
    val horaDispositivoMs: Long,
    val estado: String,
    val motivoRechazo: String?,
    val nota: String?,
    val intentos: Int,
    val ultimoIntentoMs: Long?,
    /** A1 (versión 4): recibo del servidor al aceptarlo (201/200): quién registró y la hora del servidor. */
    val registradoPorId: Int? = null,
    val registradoPor: String? = null,
    val fechaServidor: String? = null,
)

/**
 * A2 (versión 4): la última respuesta de GET /obras/{id}/almacen/stock, tal cual (JSON), y cuándo llegó.
 * Sin señal se muestra esta copia con su hora. El celular no la recalcula ni la ajusta con lo registrado.
 */
@Entity(tableName = "stock_guardado")
data class StockGuardadoEntity(@PrimaryKey val obraId: Int, val json: String, val recibidoMs: Long)

/** Copia de los trabajadores de cada obra, para tomar asistencia sin señal (HU-63). */
@Entity(tableName = "trabajador_local")
data class TrabajadorLocalEntity(
    @PrimaryKey val id: Int,
    val obraId: Int,
    val nombre: String,
    val tipoDocumento: String,
    val documento: String,
    val categoria: String,
    val cuadrilla: String,
    val fechaIngreso: String,
    val fechaSalida: String?,
)

@Dao
interface AjusteDao {
    @Upsert suspend fun guardar(a: AjusteEntity)
    @Query("SELECT valor FROM ajuste WHERE clave = :clave") suspend fun leer(clave: String): String?
    @Query("SELECT * FROM ajuste") fun observar(): Flow<List<AjusteEntity>>
    @Query("DELETE FROM ajuste WHERE clave IN (:claves)") suspend fun quitar(claves: List<String>)
}

@Dao
interface SalidaDao {
    /** ABORT: un uuid repetido en el celular es un error de programación, no se pisa. */
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertar(s: SalidaLocalEntity)

    @Query("SELECT * FROM salida_local WHERE obraId = :obraId ORDER BY horaDispositivoMs DESC")
    fun observarDeObra(obraId: Int): Flow<List<SalidaLocalEntity>>

    @Query("SELECT * FROM salida_local WHERE estado = 'PENDIENTE' ORDER BY horaDispositivoMs")
    suspend fun pendientes(): List<SalidaLocalEntity>

    @Query("SELECT COUNT(*) FROM salida_local WHERE estado = 'PENDIENTE'")
    fun contarPendientes(): Flow<Int>

    @Query(
        "UPDATE salida_local SET estado = :estado, motivoRechazo = :motivoRechazo, nota = :nota, " +
            "intentos = intentos + 1, ultimoIntentoMs = :ahora WHERE uuidCliente = :uuid"
    )
    suspend fun marcar(uuid: String, estado: String, motivoRechazo: String?, nota: String?, ahora: Long)

    @Query("UPDATE salida_local SET registradoPorId = :porId, registradoPor = :por, fechaServidor = :fecha WHERE uuidCliente = :uuid")
    suspend fun anotarRecibo(uuid: String, porId: Int?, por: String?, fecha: String?)
}

@Dao
interface InsumoDao {
    @Upsert suspend fun guardarTodos(lista: List<InsumoLocalEntity>)

    @Query(
        "SELECT * FROM insumo_local WHERE codigo LIKE '%' || :q || '%' OR descripcion LIKE '%' || :q || '%' " +
            "ORDER BY descripcion LIMIT 50"
    )
    suspend fun buscar(q: String): List<InsumoLocalEntity>

    @Query("SELECT COUNT(*) FROM insumo_local") suspend fun contar(): Int
}

@Dao
interface RegistroDao {
    /** ABORT: un uuid repetido en el celular es un error de programación, no se pisa. */
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertar(r: RegistroLocalEntity)

    @Query("SELECT * FROM registro_local WHERE obraId = :obraId ORDER BY horaDispositivoMs DESC")
    fun observarDeObra(obraId: Int): Flow<List<RegistroLocalEntity>>

    @Query("SELECT * FROM registro_local WHERE estado = 'PENDIENTE' AND tipo IN (:tipos) ORDER BY horaDispositivoMs")
    suspend fun pendientes(tipos: List<String>): List<RegistroLocalEntity>

    @Query("SELECT COUNT(*) FROM registro_local WHERE estado = 'PENDIENTE' AND tipo IN (:tipos)")
    fun contarPendientes(tipos: List<String>): Flow<Int>

    @Query("SELECT clave FROM registro_local WHERE obraId = :obraId AND clave LIKE :prefijo || '%' AND estado != 'RECHAZADO'")
    suspend fun clavesVigentes(obraId: Int, prefijo: String): List<String>

    @Query(
        "UPDATE registro_local SET estado = :estado, motivoRechazo = :motivoRechazo, nota = :nota, " +
            "intentos = intentos + 1, ultimoIntentoMs = :ahora WHERE uuidCliente = :uuid"
    )
    suspend fun marcar(uuid: String, estado: String, motivoRechazo: String?, nota: String?, ahora: Long)

    @Query("UPDATE registro_local SET registradoPorId = :porId, registradoPor = :por, fechaServidor = :fecha WHERE uuidCliente = :uuid")
    suspend fun anotarRecibo(uuid: String, porId: Int?, por: String?, fecha: String?)
}

@Dao
interface StockDao {
    @Upsert suspend fun guardar(s: StockGuardadoEntity)
    @Query("SELECT * FROM stock_guardado WHERE obraId = :obraId") suspend fun leer(obraId: Int): StockGuardadoEntity?
}

@Dao
interface TrabajadorDao {
    @Upsert suspend fun guardarTodos(lista: List<TrabajadorLocalEntity>)

    @Query("SELECT * FROM trabajador_local WHERE obraId = :obraId ORDER BY cuadrilla, nombre")
    fun observarDeObra(obraId: Int): Flow<List<TrabajadorLocalEntity>>
}

@Database(
    entities = [
        AjusteEntity::class, SalidaLocalEntity::class, InsumoLocalEntity::class,
        RegistroLocalEntity::class, TrabajadorLocalEntity::class, StockGuardadoEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // 1 → 2 solo agrega tablas: la cola de salidas que ya está en el celular no se toca.
    // 2 → 3 agrega salida_local.almacenId (nulo en lo anterior) y cierra las entradas de compra viejas.
    // 3 → 4 agrega el recibo del servidor (A1), la tabla stock_guardado (A2) y cierra los ingresos sin compra viejos (A5).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = Migracion2a3::class),
        AutoMigration(from = 3, to = 4, spec = Migracion3a4::class),
    ],
)
@ConstructedBy(ConstructorBaseDatos::class)
abstract class BaseDatosSigro : RoomDatabase() {
    abstract fun ajustes(): AjusteDao
    abstract fun salidas(): SalidaDao
    abstract fun insumos(): InsumoDao
    abstract fun registros(): RegistroDao
    abstract fun trabajadores(): TrabajadorDao
    abstract fun stock(): StockDao
}

/**
 * Hasta la versión 2 la entrada de compra se guardaba con un cuerpo propuesto (con `insumo_codigo` y
 * `con_foto_guia`) que la API real rechaza. Esas filas no se envían: quedan RECHAZADAS con el motivo y a la
 * vista en «Mis registros» (nada se borra), para registrarlas de nuevo con la pantalla nueva.
 */
class Migracion2a3 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE registro_local SET estado = 'RECHAZADO', motivoRechazo = '$MOTIVO_ENTRADA_ANTIGUA' " +
                "WHERE tipo = 'ENTRADA_COMPRA' AND estado = 'PENDIENTE'"
        )
    }
}

/**
 * Hasta la versión 3 el ingreso sin compra se guardaba con un cuerpo propuesto (`detalle`, orígenes con otros
 * nombres, sin `dispositivo`) que `IngresoIn` rechaza. Igual que en 2→3: quedan RECHAZADOS con su motivo, a la vista.
 */
class Migracion3a4 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE registro_local SET estado = 'RECHAZADO', motivoRechazo = '$MOTIVO_INGRESO_ANTIGUO' " +
                "WHERE tipo = 'INGRESO_SIN_COMPRA' AND estado = 'PENDIENTE'"
        )
    }
}

const val MOTIVO_INGRESO_ANTIGUO =
    "Se guardó con una versión anterior de la app, antes de que el servidor recibiera ingresos. Regístralo de nuevo."

const val MOTIVO_ENTRADA_ANTIGUA =
    "Se guardó con una versión anterior de la app, antes de que el servidor recibiera entradas. Regístrala de nuevo."

/** Room genera la implementación para cada plataforma (KSP). */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object ConstructorBaseDatos : RoomDatabaseConstructor<BaseDatosSigro> {
    override fun initialize(): BaseDatosSigro
}

/** Cada plataforma solo dice DÓNDE vive el archivo; el resto es común. */
fun construirBaseDatos(builder: RoomDatabase.Builder<BaseDatosSigro>): BaseDatosSigro =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
