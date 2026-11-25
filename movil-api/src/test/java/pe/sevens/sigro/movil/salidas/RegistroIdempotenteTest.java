package pe.sevens.sigro.movil.salidas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegistroIdempotenteTest {
    static class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-11-25T17:04:00Z");
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return ahora; }
    }

    @Test
    void venceALas24Horas() {
        var reloj = new RelojMovil();
        var r = new RegistroIdempotente(Optional.of(reloj));
        r.guardar("1:a", 201, new byte[] { 1 }, 42);
        assertThat(r.buscar("1:a", 42)).isPresent();
        reloj.ahora = reloj.ahora.plus(Duration.ofHours(25));
        assertThat(r.buscar("1:a", 42)).isEmpty();
    }

    @Test
    void otraObraOtroContenidoNoCoinciden() {
        var r = new RegistroIdempotente(Optional.empty());
        r.guardar(RegistroIdempotente.clave(1, "u"), 201, new byte[0], 7);
        assertThat(r.buscar(RegistroIdempotente.clave(2, "u"), 7)).isEmpty();
        assertThat(r.buscar(RegistroIdempotente.clave(1, "u"), 8)).isEmpty();
    }

    @Test
    void tieneTope() {
        var r = new RegistroIdempotente(Optional.empty());
        for (int i = 0; i < RegistroIdempotente.TOPE + 10; i++) r.guardar("1:" + i, 201, new byte[0], i);
        assertThat(r.tamano()).isEqualTo(RegistroIdempotente.TOPE);
        assertThat(r.buscar("1:0", 0)).isEmpty();
    }
}
