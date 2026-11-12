# R8 de la versión release (docs/rendimiento-app.md). Las librerías traen casi todas sus reglas
# (consumer rules): kotlinx.serialization, Room, WorkManager, OkHttp, Compose. Aquí va solo lo que falta.

# Ktor referencia clases de la JVM de escritorio y de SLF4J que en Android no existen ni se usan.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**

# DTOs de la API: kotlinx.serialization los encuentra por su serializer() generado; las reglas de la
# librería ya conservan esos métodos. Se conservan además los nombres de los campos por si un día se
# lee un JSON con reflexión desde una herramienta de soporte (no cuesta casi nada: son pocas clases).
-keep,includedescriptorclasses class pe.sevens.sigro.datos.remoto.**$$serializer { *; }
-keepclassmembers class pe.sevens.sigro.datos.remoto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Entidades y DAO de Room: Room genera el código (KSP) y su consumer rule conserva el _Impl.
# El constructor de la base (expect/actual de Room KMP) se busca por nombre en Android.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class pe.sevens.sigro.datos.local.ConstructorBaseDatos { *; }

# Perfil de arranque (src/androidMain/baselineProfiles): sus reglas usan comodines por paquete
# (pe/sevens/sigro/**) y se aplican después de R8. Si R8 mudara las clases de la app a otro paquete,
# el perfil no las encontraría: se conservan los nombres de paquete (las clases igual se acortan).
-keeppackagenames pe.sevens.sigro.**
