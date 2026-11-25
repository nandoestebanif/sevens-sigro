package pe.sevens.sigro

import kotlinx.coroutines.test.runTest
import pe.sevens.sigro.datos.repositorio.RepositorioCandadoImpl
import pe.sevens.sigro.dominio.AjusteHuella
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.CuentaConHuella
import pe.sevens.sigro.dominio.DisponibilidadHuella
import pe.sevens.sigro.dominio.ResultadoHuella
import pe.sevens.sigro.dominio.ResultadoSecreto
import pe.sevens.sigro.dominio.SinBiometria
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* Lo que la huella deja guardado en el celular (sobre la tabla de ajustes de Room, con dobles) y el equipo sin huella. */
class CandadoGuardadoTest {
    private fun repo(ajustes: AjustesDePrueba = AjustesDePrueba()) = RepositorioCandadoImpl(credencialesDePrueba(ajustes)) to ajustes

    @Test
    fun elAjusteSeGuardaPorUsuario() = runTest {
        val (r, _) = repo()
        assertEquals(AjusteHuella(), r.ajuste(7), "sin guardar: huella apagada")
        r.guardar(7, AjusteHuella(activa = true, minutos = 5, ofrecida = true))
        assertEquals(AjusteHuella(activa = true, minutos = 5, ofrecida = true), r.ajuste(7))
        assertEquals(AjusteHuella(), r.ajuste(8), "otra persona en el mismo celular no hereda la huella")
    }

    @Test
    fun elUltimoUsoSobreviveAQueSeCierreLaApp() = runTest {
        val (r, _) = repo()
        assertNull(r.ultimoUso())
        r.guardarUltimoUso(123_456L)
        assertEquals(123_456L, r.ultimoUso())
    }

    @Test
    fun laCuentaConHuellaSobreviveACerrarSesionYSeBorraConNoSoyYo() = runTest {
        val ajustes = AjustesDePrueba()
        val (r, _) = repo(ajustes)
        val c = CuentaConHuella(7, "adiel", "Adiel Luján", "https://api.prueba", "aXY=:Y2lm")
        r.guardarCuenta(c)
        pe.sevens.sigro.datos.repositorio.BovedaSesion(AlmacenSeguroEnMemoria(), ajustes).cerrarSesion() // lo que hace «cerrar sesión»
        assertEquals(c, r.cuenta(), "cerrar sesión no la borra: es para volver a entrar con la huella")
        r.olvidarCuenta()
        assertNull(r.cuenta())
    }

    @Test
    fun sinHuellaNoSeOfreceNiSeGuardaNada() = runTest {
        val b: Biometria = SinBiometria
        assertEquals(DisponibilidadHuella.NO_DISPONIBLE, b.disponibilidad())
        assertTrue(b.verificar("t", "d") is ResultadoHuella.Fallo)
        assertEquals(false, b.guardaClaves())
        assertEquals(ResultadoSecreto.NoSoportado, b.cifrar("t", "d", "clave"))
        assertEquals(ResultadoSecreto.NoSoportado, b.descifrar("t", "d", "sobre"))
        b.olvidar(); b.olvidarClave() // no hacen nada y no fallan
    }
}
