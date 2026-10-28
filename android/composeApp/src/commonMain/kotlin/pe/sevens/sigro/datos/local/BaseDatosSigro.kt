package pe.sevens.sigro.datos.local

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
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

/*
 * Base local del celular (Room KMP: la misma en Android e iOS, con SQLite empaquetado).
 * Guarda lo que tiene que sobrevivir a que el sistema cierre la app sin señal (ADR-02, HU-37).
 */

/** Ajustes clave → valor: servidor, sesión, obra elegida, identificador del dispositivo. */
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
)

/** Copia del catálogo para buscar insumos en obra sin señal. Se refresca con señal. */
@Entity(tableName = "insumo_local")
data class InsumoLocalEntity(
    @PrimaryKey val id: Int,
    val codigo: String,
    val descripcion: String,
    val unidad: String,
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

@Database(
    entities = [AjusteEntity::class, SalidaLocalEntity::class, InsumoLocalEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ConstructorBaseDatos::class)
abstract class BaseDatosSigro : RoomDatabase() {
    abstract fun ajustes(): AjusteDao
    abstract fun salidas(): SalidaDao
    abstract fun insumos(): InsumoDao
}

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
