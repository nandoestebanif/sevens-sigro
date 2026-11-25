package pe.sevens.sigro.comun;

import org.springframework.http.ResponseEntity;

/** Respuestas con estado: 201 creado y 204 sin contenido. */
public final class C {
    private C() {}
    public static <T> ResponseEntity<T> creado(T cuerpo) { return ResponseEntity.status(201).body(cuerpo); }
    public static ResponseEntity<Void> vacio() { return ResponseEntity.noContent().build(); }
    public static <T> ResponseEntity<T> con(int estado, T cuerpo) { return ResponseEntity.status(estado).body(cuerpo); }
}
