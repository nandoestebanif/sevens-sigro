/* ─────────────────────────────────────────────────────────────────────────────
   HU-07 · Como residente de obra, quiero importar el catálogo de insumos del
           S10, para evitar cargar a mano cientos de insumos.
   Lienzo W3 «Importar del S10» (estilo vidrio).

   Criterios de la tarjeta y dónde se ven:
   - Acepta el archivo de recursos exportado del S10 → se sube el PDF real.
   - Un insumo ya existente se actualiza, no se duplica → columna «acción»
     (el interruptor «la obra ya tiene catálogo» simula a la API mandando los
     códigos que ya existen).
   - El resultado informa cuántos se crearon, actualizaron y fallaron →
     comprobaciones al leer y aviso al confirmar.
   - Puede repetirse sin alterar los saldos → el parser no toca ninguna base y
     los saldos viven en los movimientos; repetir es volver a leer.
   El PDF lo procesa el micro de integraciones; la web solo lo sube y muestra.
   ───────────────────────────────────────────────────────────────────────── */
import { useRef, useState, type ReactNode } from 'react'
import { api } from '../api/cliente'
import { ErrorImportacion, importarRelacionS10, type ResultadoImportacion } from '../api/s10'
import { usaApiReal } from '../api/http'
import { ErrorApi } from '../api/error'
import type { ResultadoRequerimiento } from '../api/tipos'
import { Chip } from '../ui/base'
import { Encabezado, Pildora, TituloSeccion, Vidrio } from '../ui/piezas'

const n = (v: number, d = 0) => v.toLocaleString('es-PE', { minimumFractionDigits: d, maximumFractionDigits: d === 0 ? 4 : d })
const TIPO: Record<string, string> = {
  mano_obra: 'Mano de obra', material: 'Materiales', equipo: 'Equipos', subcontrato: 'Subcontratos',
}
const ORDEN = ['material', 'mano_obra', 'equipo', 'subcontrato']
const PASOS = ['Subir el PDF', 'Revisar lo leído', 'Confirmar']

export function Importar() {
  const [archivo, setArchivo] = useState<File | null>(null)
  const [conCatalogo, setConCatalogo] = useState(false)
  const [resultado, setResultado] = useState<ResultadoImportacion | null>(null)
  const [error, setError] = useState('')
  const [subiendo, setSubiendo] = useState(false)
  const [aplicando, setAplicando] = useState(false)
  const [aplicado, setAplicado] = useState<{ creados: number; actualizados: number; sinCambios: number; advertencias: string[] } | null>(null)
  const [requerimiento, setRequerimiento] = useState<ResultadoRequerimiento | null>(null)
  const [cargandoReq, setCargandoReq] = useState(false)
  const [pestana, setPestana] = useState<string | null>(null)
  const entrada = useRef<HTMLInputElement>(null)

  const importar = async () => {
    if (!archivo) return
    setSubiendo(true)
    setError('')
    setResultado(null)
    setAplicado(null)
    setRequerimiento(null)
    try {
      // con la API real, los códigos que ya existen los pone el catálogo del servidor;
      // con el simulado, «la obra ya tiene catálogo» los toma de la relación actual
      const existentes = conCatalogo && !usaApiReal ? (await api.listarInsumos()).map((i) => i.codigo) : []
      setResultado(await importarRelacionS10(archivo, existentes))
    } catch (e) {
      setError(e instanceof ErrorImportacion ? e.message : 'No se pudo importar el archivo.')
    } finally {
      setSubiendo(false)
    }
  }

  /** HU-07: el micro solo LEE el PDF (ADR-01); el catálogo lo escribe la API */
  const aplicar = async () => {
    if (!resultado) return
    setAplicando(true)
    setError('')
    try {
      setAplicado(await api.aplicarImportacionS10(
        resultado.insumos.map((i) => ({ codigo: i.codigo, descripcion: i.descripcion, unidad: i.unidad, tipo: i.tipo })),
      ))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo aplicar la importación.')
    } finally {
      setAplicando(false)
    }
  }

  /** HU-19: lo planificado (cantidad y precio del S10) queda como requerimiento de la obra activa.
      Va después del catálogo porque el servidor solo acepta insumos que el catálogo conoce. */
  const cargarReq = async () => {
    if (!resultado) return
    setCargandoReq(true)
    setError('')
    try {
      setRequerimiento(await api.cargarRequerimiento(
        resultado.insumos.map((i) => ({ insumoCodigo: i.codigo, cantidad: i.cantidad, precio: i.precio })),
      ))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar el requerimiento.')
    } finally {
      setCargandoReq(false)
    }
  }

  const r = resultado
  // los tipos en el orden del lienzo (materiales primero); la pestaña elegida, o la primera
  const tipos = r ? Object.keys(r.resumen.por_tipo).sort((a, b) => ORDEN.indexOf(a) - ORDEN.indexOf(b)) : []
  const tipoVisto = pestana && tipos.includes(pestana) ? pestana : tipos[0]
  const filasDelTipo = r ? r.insumos.filter((i) => i.tipo === tipoVisto) : []
  const parcialDelTipo = (t: string) => (r ? r.insumos.filter((i) => i.tipo === t).reduce((s, i) => s + i.parcial, 0) : 0)
  const paso = !r ? 1 : requerimiento ? 4 : aplicado ? 3 : 2
  const avisoError = error && <p role="alert" className="text-danger fw-semibold">{error}</p>

  return (
    <>
      <div className="d-flex flex-column gap-3 pb-4">
        <Encabezado
          titulo="Importar del S10"
          sub="Relación de insumos del presupuesto: la base de la obra, sin escribir nada a mano"
        />

        <ol aria-label="Pasos de la importación" className="m-0 px-1 list-unstyled d-flex flex-wrap" style={{ gap: '10px 22px' }}>
          {PASOS.map((t, i) => {
            const estado = i + 1 < paso ? 'hecho' : i + 1 === paso ? 'actual' : 'pendiente'
            return (
              <li
                key={t}
                aria-current={estado === 'actual' ? 'step' : undefined}
                style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 14, fontWeight: 700, color: estado === 'pendiente' ? 'var(--tinta-2)' : 'var(--tinta)' }}
              >
                <span
                  aria-hidden
                  style={{
                    width: 30, height: 30, borderRadius: 15, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13,
                    background: estado === 'hecho' ? 'var(--bien)' : estado === 'actual' ? 'var(--menu-activo)' : 'var(--separador)',
                    color: estado === 'hecho' ? 'var(--tarjeta)' : estado === 'actual' ? 'var(--menu-activo-tinta)' : 'var(--tinta-2)',
                  }}
                >
                  {estado === 'hecho' ? '✓' : i + 1}
                </span>
                {t}{estado === 'hecho' ? ' · hecho' : ''}
              </li>
            )
          })}
        </ol>

        <div className="d-flex flex-wrap gap-3 align-items-stretch">
          <Vidrio estilo={{ flex: '1 1 340px', display: 'flex', flexDirection: 'column', gap: 14 }}>
            <input
              ref={entrada}
              type="file"
              accept="application/pdf"
              aria-label="PDF de la Relación de insumos del S10"
              className="d-none form-control"
              onChange={(e) => { setArchivo(e.target.files?.[0] ?? null); setResultado(null); setError('') }}
            />
            <div className="rounded-pill bg-primary-subtle d-flex flex-wrap align-items-center gap-3" style={{ border: '2px dashed var(--accion)', padding: 'clamp(14px, calc(3vw / var(--escala, 1)), 22px)' }}>
              <span aria-hidden className="d-flex align-items-center justify-content-center small fw-bold text-danger flex-shrink-0 rounded-3 bg-body border"  style={{ width: 52, height: 60 }}>PDF</span>
              <span className="d-flex flex-column gap-1 min-w-0"  style={{ flex: '1 1 160px' }}>
                <span className="fw-bold" style={{ overflowWrap: 'anywhere' }}>{archivo ? archivo.name : 'Elige la Relación de insumos'}</span>
                <span className="small text-body-secondary">
                  {r
                    ? `${r.obra ?? 'Obra sin identificar'}${r.subpresupuesto ? ` · subpresupuesto ${r.subpresupuesto}` : ''}${r.fecha ? ` · ${r.fecha}` : ''}`
                    : 'El PDF exportado del S10: precios y cantidades de recursos requeridos por tipo'}
                </span>
              </span>
              <Pildora tipo="secundario" enClic={() => entrada.current?.click()}>{archivo ? 'Cambiar' : 'Elegir el PDF'}</Pildora>
            </div>
            {!usaApiReal && (
              <label className="d-flex align-items-center gap-2 text-body-secondary" style={{ minHeight: 44 }}>
                <input className="form-check-input mt-0" type="checkbox" checked={conCatalogo} onChange={(e) => setConCatalogo(e.target.checked)} />
                la obra ya tiene catálogo (los repetidos se actualizan)
              </label>
            )}
            {r ? (
              <ul className="m-0 p-0 list-unstyled d-flex flex-column gap-2">
                <Comprobacion bien={r.resumen.fallidos === 0}>{r.resumen.leidos} filas leídas, {r.resumen.fallidos} fallidas</Comprobacion>
                <Comprobacion bien>{r.resumen.a_crear} a crear · {r.resumen.a_actualizar} a actualizar: el código es la clave, nada se duplica</Comprobacion>
                <Comprobacion bien>Leer no toca ningún saldo: el catálogo no es el almacén</Comprobacion>
              </ul>
            ) : (
              <>
                {avisoError}
                <div className="d-flex justify-content-end">
                  <Pildora enClic={importar} deshabilitado={!archivo || subiendo}>{subiendo ? 'Leyendo el archivo…' : 'Leer el archivo'}</Pildora>
                </div>
              </>
            )}
          </Vidrio>

          <Vidrio estilo={{ flex: '1 1 340px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {r ? (
              <>
                <span className="small fw-semibold text-body-secondary">Total del reporte</span>
                <span className="d-flex align-items-baseline gap-2">
                  <span className="fs-6 fw-bold text-body-secondary">S/</span>
                  <span className="n fw-bold"  style={{ fontSize: 'clamp(28px, calc(4vw / var(--escala, 1)), 38px)' }}>{n(r.resumen.parcial_total, 2)}</span>
                </span>
                <div className="gap-2" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
                  {tipos.map((t) => (
                    <div key={t} className="d-flex flex-column bg-body-tertiary rounded-4 py-2 px-2" >
                      <span className="small text-body-secondary">{TIPO[t] ?? t}</span>
                      <span className="n fs-5 fw-bold">{r.resumen.por_tipo[t]}</span>
                      <span className="n small text-body-secondary">S/ {n(parcialDelTipo(t), 2)}</span>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              <>
                <TituloSeccion>Así funciona</TituloSeccion>
                <p className="text-body-secondary">
                  Es el paso que sigue a crear la obra (HU-01): su Relación de insumos se sube una vez y la relación de la obra
                  queda cargada. El archivo lo procesa el servicio de integraciones: cada insumo llega con su código, unidad,
                  cantidad y precio planificados. Un código que ya existe se actualiza (jamás se duplica), una fila ilegible se
                  informa con su motivo, y repetir la importación no toca ningún saldo. Se repite solo si el presupuesto del S10 cambia.
                </p>
              </>
            )}
          </Vidrio>
        </div>

        {r && r.fallidos.length > 0 && (
          <Vidrio estilo={{ borderLeft: '4px solid var(--grave)' }}>
            <TituloSeccion>Filas que no se entendieron</TituloSeccion>
            <p className="small text-body-secondary" style={{ margin: '4px 0 8px' }}>Se informan, no se descartan en silencio.</p>
            {r.fallidos.map((f, i) => (
              <div key={i} className="small text-body-secondary py-1 px-0" >
                <span className="n">{f.texto}</span> — {f.error}
              </div>
            ))}
          </Vidrio>
        )}

        {r && (
          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
              <TituloSeccion>Lo que se va a importar</TituloSeccion>
              <div role="tablist" aria-label="Tipo de insumo" className="d-flex flex-wrap gap-2">
                {tipos.map((t) => {
                  const activo = t === tipoVisto
                  return (
                    <button className={`btn btn-sm rounded-pill ${activo ? 'btn-primary' : 'btn-outline-secondary'}`}
                      key={t} type="button" role="tab" aria-selected={activo}
                      onClick={() => setPestana(t)}

                    >
                      {TIPO[t] ?? t} · {r.resumen.por_tipo[t]}
                    </button>
                  )
                })}
              </div>
            </div>
            <div role="tabpanel" className="overflow-x-auto">
              <table className="table table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>Código S10</th><th>Descripción</th><th>Und.</th>
                    <th className="n">Cantidad</th><th className="n">Precio S/</th><th className="n">Parcial S/</th><th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {filasDelTipo.slice(0, 40).map((ins) => (
                    <tr key={ins.codigo}>
                      <td className="n text-body-secondary text-start">{ins.codigo}</td>
                      <td className="fw-semibold">{ins.descripcion}</td>
                      <td>{ins.unidad}</td>
                      <td className="n">{n(ins.cantidad)}</td>
                      <td className="n">{n(ins.precio, 2)}</td>
                      <td className="n fw-bold">{n(ins.parcial, 2)}</td>
                      <td><Chip tono={ins.accion === 'crear' ? 'bien' : 'aviso'}>{ins.accion === 'crear' ? 'Crear' : 'Actualizar'}</Chip></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <span className="small text-body-secondary">
              Vista previa: {Math.min(40, filasDelTipo.length)} de {filasDelTipo.length} · {(TIPO[tipoVisto] ?? tipoVisto).toLowerCase()}.
            </span>
          </Vidrio>
        )}

        {r && (
          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {aplicado ? (
              <div role="status">
                <strong>Catálogo actualizado.</strong> {aplicado.creados} creados · {aplicado.actualizados} actualizados · {aplicado.sinCambios} sin cambios.
                {aplicado.advertencias.length > 0 && (
                  <div className="mt-2 small text-body-secondary">
                    {aplicado.advertencias.slice(0, 5).map((w, i) => <div key={i}>• {w}</div>)}
                    {aplicado.advertencias.length > 5 && <div>… y {aplicado.advertencias.length - 5} avisos más</div>}
                  </div>
                )}
              </div>
            ) : (
              <div className="d-flex align-items-center gap-3 flex-wrap justify-content-end">
                <span className="text-body-secondary" style={{ flex: '1 1 300px' }}>
                  Al confirmar, los insumos entran al catálogo: los códigos nuevos se crean y los que ya existen se actualizan. No se toca ningún saldo.
                </span>
                <Pildora enClic={aplicar} deshabilitado={aplicando}>
                  {aplicando ? 'Aplicando…' : `Confirmar e importar ${r.resumen.leidos} insumos`}
                </Pildora>
              </div>
            )}

            {aplicado && (requerimiento ? (
              <div role="status" className="pt-3 border-top" >
                <strong>Requerimiento de la obra cargado.</strong> {requerimiento.creados} insumos nuevos · {requerimiento.sinCambios} ya estaban
                {requerimiento.fallidos.length > 0 && ` · ${requerimiento.fallidos.length} no se cargaron`}.
                {(requerimiento.advertencias.length > 0 || requerimiento.fallidos.length > 0) && (
                  <div className="mt-2 small text-body-secondary">
                    {requerimiento.fallidos.slice(0, 3).map((f, i) => <div key={`f${i}`}>• {f.insumoCodigo}: {f.motivo}</div>)}
                    {requerimiento.advertencias.slice(0, 3).map((w, i) => <div key={`w${i}`}>• {w}</div>)}
                  </div>
                )}
              </div>
            ) : (
              <div className="d-flex align-items-center gap-3 flex-wrap justify-content-end pt-3 border-top" >
                <span className="text-body-secondary" style={{ flex: '1 1 300px' }}>
                  Siguiente: cargar lo planificado (cantidad y precio del S10) como requerimiento de la obra activa. Es el cien por ciento contra el que se controla todo; lo ya cargado no se pisa.
                </span>
                <Pildora enClic={cargarReq} deshabilitado={cargandoReq}>{cargandoReq ? 'Cargando…' : 'Cargar requerimiento de la obra'}</Pildora>
              </div>
            ))}
            {avisoError}
          </Vidrio>
        )}

        <p className="small text-body-secondary px-1">
          El emparejamiento usa el <strong>código del insumo como clave</strong>: por eso
          repetir la importación actualiza en vez de duplicar. Y nunca toca los saldos,
          porque el catálogo no es el almacén — el stock vive en los movimientos.
        </p>
      </div>
    </>
  )
}

function Comprobacion({ bien, children }: { bien: boolean; children: ReactNode }) {
  return (
    <li className="d-flex gap-2 align-items-start">
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={bien ? 'var(--bien)' : 'var(--aviso)'} strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0 mt-1">
        {bien ? <path d="M5 12.5l4.5 4.5L19 7.5" /> : <path d="M12 8v5M12 16.5v.5" />}
      </svg>
      <span>{children}</span>
    </li>
  )
}
