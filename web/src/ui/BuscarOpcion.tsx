/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · elegir una opción de una lista larga escribiendo (combobox accesible)

   Reemplaza los <select> de cientos de opciones (284 insumos del S10, 600 y tantos
   proveedores): se escribe parte del código o del nombre, sin importar tildes ni
   mayúsculas, y se elige con el ratón o con ↑ ↓ y Enter. Esc cierra la lista sin
   cerrar el modal que la contiene. Patrón ARIA «combobox con listbox».
   ───────────────────────────────────────────────────────────────────────── */
import { type KeyboardEvent, useEffect, useId, useMemo, useRef, useState } from 'react'

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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
      <label htmlFor={id} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>{etiqueta}</label>
      <div style={{ position: 'relative' }}>
      <input
        id={id}
        role="combobox"
        aria-expanded={abierta}
        aria-controls={idLista}
        aria-autocomplete="list"
        aria-activedescendant={idActiva}
        aria-invalid={error ? true : undefined}
        aria-describedby={ayuda || error ? `${id}-ayuda` : undefined}
        autoComplete="off"
        value={texto}
        placeholder={marcador}
        onChange={(e) => { cancelarCierre(); setTexto(e.target.value); setAbierta(true); setActiva(0); if (valor) enCambio('') }}
        onFocus={cancelarCierre}
        onClick={() => setAbierta(true)}
        onBlur={() => { cancelarCierre(); cierre.current = setTimeout(() => setAbierta(false), 120) }}
        onKeyDown={teclas}
        style={{
          width: '100%', minWidth: 0, height: 42, border: `1px solid ${error ? 'var(--grave)' : 'var(--borde)'}`, borderRadius: 'var(--radio-chico)',
          padding: '0 13px', fontSize: 14, fontFamily: 'inherit', color: 'var(--tinta)', background: 'var(--tarjeta)', outlineColor: 'var(--accion)',
        }}
      />
      {abierta && (
        <ul
          id={idLista}
          role="listbox"
          aria-label={etiqueta}
          style={{
            position: 'absolute', top: 'calc(100% + 4px)', left: 0, right: 0, zIndex: 30,
            listStyle: 'none', margin: 0, padding: 4, maxHeight: 240, overflowY: 'auto', overscrollBehavior: 'contain',
            border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)', background: 'var(--tarjeta)',
            boxShadow: '0 12px 30px rgba(20,20,20,0.18)',
          }}
        >
          {visibles.length === 0 && <li style={{ padding: '10px 12px', fontSize: 13, color: 'var(--tinta-3)' }}>{sinResultados} «{texto}».</li>}
          {visibles.map((o, i) => (
            <li
              key={o.valor}
              id={`${id}-op-${i}`}
              role="option"
              aria-selected={o.valor === valor}
              onMouseDown={(e) => { e.preventDefault(); elegir(o) }}
              onMouseEnter={() => setActiva(i)}
              style={{
                padding: '8px 12px', borderRadius: 8, cursor: 'pointer',
                background: i === activa ? 'var(--marca-tinte)' : 'transparent',
              }}
            >
              <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--tinta)' }}>{o.texto}</div>
              {o.detalle && <div className="n" style={{ fontSize: 12, color: 'var(--tinta-3)' }}>{o.detalle}</div>}
            </li>
          ))}
          {total > visibles.length && (
            <li style={{ padding: '8px 12px', fontSize: 12, color: 'var(--tinta-3)' }}>
              Se muestran {visibles.length} de {total.toLocaleString('es-PE')}: escribe más para acotar.
            </li>
          )}
        </ul>
      )}
      </div>
      {(error || ayuda) && (
        <span id={`${id}-ayuda`} role={error ? 'alert' : undefined} style={{ fontSize: 12, color: error ? 'var(--grave)' : 'var(--tinta-3)' }}>
          {error || ayuda}
        </span>
      )}
    </div>
  )
}
