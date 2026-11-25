/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · elegir una opción de una lista larga escribiendo (combobox accesible)

   Reemplaza los <select> de cientos de opciones (284 insumos del S10, 600 y tantos
   proveedores): se escribe parte del código o del nombre, sin importar tildes ni
   mayúsculas, y se elige con el ratón o con ↑ ↓ y Enter. Esc cierra la lista sin
   cerrar el modal que la contiene. Patrón ARIA «combobox con listbox», con el
   aspecto de Bootstrap: Form.Control para el campo y «dropdown-menu» para la lista.
   ───────────────────────────────────────────────────────────────────────── */
import { type KeyboardEvent, useEffect, useId, useMemo, useRef, useState } from 'react'
import { Form } from 'react-bootstrap'

export interface Opcion { valor: string; texto: string; detalle?: string }

const sinTildes = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

/** Las opciones que contienen todas las palabras escritas, en el texto o en el detalle (código). */
export function filtrarOpciones(opciones: Opcion[], consulta: string, tope = 50): { visibles: Opcion[]; total: number } {
  const palabras = sinTildes(consulta).split(/\s+/).filter(Boolean)
  const todas = palabras.length
    ? opciones.filter((o) => { const t = sinTildes(`${o.texto} ${o.detalle ?? ''} ${o.valor}`); return palabras.every((p) => t.includes(p)) })
    : opciones
  return { visibles: todas.slice(0, tope), total: todas.length }
}

export function BuscarOpcion({ id, etiqueta, opciones, valor, enCambio, marcador, error, ayuda, sinResultados = 'Nada coincide con' }: {
  id: string
  etiqueta: string
  opciones: Opcion[]
  valor: string
  enCambio: (valor: string) => void
  marcador?: string
  error?: string
  ayuda?: string
  sinResultados?: string
}) {
  const elegida = opciones.find((o) => o.valor === valor) ?? null
  const [texto, setTexto] = useState(elegida?.texto ?? '')
  const [abierta, setAbierta] = useState(false)
  const [activa, setActiva] = useState(0)
  const idLista = useId()
  // el cierre al perder el foco espera un instante (para que el clic en una opción llegue); si el foco
  // vuelve o se escribe antes, ese cierre se cancela: si no, cerraba la lista recién abierta
  const cierre = useRef<ReturnType<typeof setTimeout> | null>(null)
  const cancelarCierre = () => { if (cierre.current) { clearTimeout(cierre.current); cierre.current = null } }
  useEffect(() => cancelarCierre, [])
  const { visibles, total } = useMemo(() => filtrarOpciones(opciones, abierta && texto !== elegida?.texto ? texto : ''), [opciones, texto, abierta, elegida])

  const elegir = (o: Opcion) => { enCambio(o.valor); setTexto(o.texto); setAbierta(false) }
  // si el valor llega desde fuera (p. ej. «+ Nuevo» proveedor), el campo muestra su texto
  useEffect(() => { if (elegida) setTexto(elegida.texto) }, [valor, elegida?.texto])

  const teclas = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setAbierta(true); setActiva((i) => Math.min(i + 1, visibles.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiva((i) => Math.max(i - 1, 0)) }
    else if (e.key === 'Enter' && abierta && visibles[activa]) { e.preventDefault(); elegir(visibles[activa]) }
    else if (e.key === 'Escape' && abierta) { e.stopPropagation(); setAbierta(false); setTexto(elegida?.texto ?? '') }
  }

  const idActiva = abierta && visibles[activa] ? `${id}-op-${activa}` : undefined
  return (
    <Form.Group controlId={id} className="min-w-0">
      <Form.Label className="small fw-semibold mb-1">{etiqueta}</Form.Label>
      {/* position-relative: la lista se ubica debajo del campo, como un Dropdown.Menu de Bootstrap */}
      <div className="position-relative">
        <Form.Control
          role="combobox"
          aria-expanded={abierta}
          aria-controls={idLista}
          aria-autocomplete="list"
          aria-activedescendant={idActiva}
          aria-describedby={ayuda || error ? `${id}-ayuda` : undefined}
          isInvalid={!!error}
          autoComplete="off"
          value={texto}
          placeholder={marcador}
          onChange={(e) => { cancelarCierre(); setTexto(e.target.value); setAbierta(true); setActiva(0); if (valor) enCambio('') }}
          onFocus={cancelarCierre}
          onClick={() => setAbierta(true)}
          onBlur={() => { cancelarCierre(); cierre.current = setTimeout(() => setAbierta(false), 120) }}
          onKeyDown={teclas}
        />
        {abierta && (
          // «dropdown-menu show»: el estilo del menú desplegable de Bootstrap; cada opción es un «dropdown-item»
          <ul id={idLista} role="listbox" aria-label={etiqueta}
              className="dropdown-menu show w-100 shadow overflow-auto" style={{ maxHeight: 260 }}>
            {visibles.length === 0 && <li className="dropdown-item-text small text-body-secondary">{sinResultados} «{texto}».</li>}
            {visibles.map((o, i) => (
              <li
                key={o.valor}
                id={`${id}-op-${i}`}
                role="option"
                aria-selected={o.valor === valor}
                className={`dropdown-item text-wrap${i === activa ? ' active' : ''}`}
                onMouseDown={(e) => { e.preventDefault(); elegir(o) }}
                onMouseEnter={() => setActiva(i)}
              >
                <div className="fw-semibold small">{o.texto}</div>
                {o.detalle && <div className={`n small ${i === activa ? '' : 'text-body-secondary'}`}>{o.detalle}</div>}
              </li>
            ))}
            {total > visibles.length && (
              <li className="dropdown-item-text small text-body-secondary">
                Se muestran {visibles.length} de {total.toLocaleString('es-PE')}: escribe más para acotar.
              </li>
            )}
          </ul>
        )}
      </div>
      {error && <Form.Control.Feedback type="invalid" id={`${id}-ayuda`} className="d-block" role="alert">{error}</Form.Control.Feedback>}
      {!error && ayuda && <Form.Text id={`${id}-ayuda`} muted>{ayuda}</Form.Text>}
    </Form.Group>
  )
}
