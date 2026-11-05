package pe.sevens.sigro.plataforma

/**
 * Almacén cifrado del celular para secretos (hoy, solo el token de sesión). La base local (Room) no se
 * cifra: lo que guarda no es secreto, y el token ya no vive ahí.
 *
 * Cada plataforma pone su implementación en su `moduloPlataforma` (el punto expect/actual de Koin):
 *  - Android: clave AES-256/GCM en el Android Keystore (no sale del hardware) + SharedPreferences con el
 *    texto cifrado (`AlmacenSeguroAndroid`);
 *  - iOS: Keychain, accesible tras el primer desbloqueo y solo en este dispositivo (`AlmacenSeguroIos`);
 *  - escritorio: archivo en ~/.sigro, sin cifrar (no es producto, solo sirve para ver la app en la PC).
 * En las pruebas se usa un doble en memoria.
 */
interface AlmacenSeguro {
    suspend fun leer(clave: String): String?
    suspend fun guardar(clave: String, valor: String)
    suspend fun borrar(clave: String)
}
