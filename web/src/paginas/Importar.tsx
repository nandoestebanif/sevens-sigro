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
import { Encabezado, Fondo, Pildora, TituloSeccion, Vidrio } from '../ui/vidrio'

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
  const avisoError = error && <p role="alert" style={{ fontSize: 14, color: 'var(--grave)', fontWeight: 600 }}>{error}</p>

  return (
    <>
      <Fondo dibujo="importar" />
      <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Encabezado
          titulo="Importar del S10"
          sub="Relación de insumos del presupuesto: la base de la obra, sin escribir nada a mano"
        />

        <ol aria-label="Pasos de la importación" style={{ margin: 0, padding: '0 4px', listStyle: 'none', display: 'flex', flexWrap: 'wrap', gap: '10px 22px' }}>
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

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'stretch' }}>
          <Vidrio estilo={{ flex: '1 1 340px', display: 'flex', flexDirection: 'column', gap: 14 }}>
            <input
              ref={entrada}
              type="file"
              accept="application/pdf"
              aria-label="PDF de la Relación de insumos del S10"
              style={{ display: 'none' }}
              onChange={(e) => { setArchivo(e.target.files?.[0] ?? null); setResultado(null); setError('') }}
            />
            <div style={{ border: '2px dashed var(--accion)', borderRadius: 22, background: 'var(--marca-tinte)', padding: 'clamp(14px, 3vw, 22px)', display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 14 }}>
              <span aria-hidden style={{ width: 52, height: 60, borderRadius: 10, background: 'var(--tarjeta)', border: '1px solid var(--campo-borde)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 800, color: 'var(--grave)', flex: 'none' }}>PDF</span>
              <span style={{ flex: '1 1 160px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 3 }}>
                <span style={{ fontSize: 15, fontWeight: 800, overflowWrap: 'anywhere' }}>{archivo ? archivo.name : 'Elige la Relación de insumos'}</span>
                <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>
                  {r
                    ? `${r.obra ?? 'Obra sin identificar'}${r.subpresupuesto ? ` · subpresupuesto ${r.subpresupuesto}` : ''}${r.fecha ? ` · ${r.fecha}` : ''}`
                    : 'El PDF exportado del S10: precios y cantidades de recursos requeridos por tipo'}
                </span>
              </span>
              <Pildora tipo="secundario" enClic={() => entrada.current?.click()}>{archivo ? 'Cambiar' : 'Elegir el PDF'}</Pildora>
            </div>
            {!usaApiReal && (
              <label style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 14, color: 'var(--tinta-2)', cursor: 'pointer', minHeight: 44 }}>
                <input type="checkbox" checked={conCatalogo} onChange={(e) => setConCatalogo(e.target.checked)} style={{ width: 20, height: 20, accentColor: 'var(--accion)' }} />
                la obra ya tiene catálogo (los repetidos se actualizan)
              </label>
            )}
            {r ? (
              <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
                <Comprobacion bien={r.resumen.fallidos === 0}>{r.resumen.leidos} filas leídas, {r.resumen.fallidos} fallidas</Comprobacion>
                <Comprobacion bien>{r.resumen.a_crear} a crear · {r.resumen.a_actualizar} a actualizar: el código es la clave, nada se duplica</Comprobacion>
                <Comprobacion bien>Leer no toca ningún saldo: el catálogo no es el almacén</Comprobacion>
              </ul>
            ) : (
              <>
                {avisoError}
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <Pildora enClic={importar} deshabilitado={!archivo || subiendo}>{subiendo ? 'Leyendo el archivo…' : 'Leer el archivo'}</Pildora>
                </div>
              </>
            )}
          </Vidrio>

          <Vidrio estilo={{ flex: '1 1 340px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {r ? (
              <>
                <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)' }}>Total del reporte</span>
                <span style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                  <span style={{ fontSize: 16, fontWeight: 700, color: 'var(--tinta-2)' }}>S/</span>
                  <span className="n" style={{ fontSize: 'clamp(28px, 4vw, 38px)', fontWeight: 800 }}>{n(r.resumen.parcial_total, 2)}</span>
                </span>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 10 }}>
                  {tipos.map((t) => (
                    <div key={t} style={{ background: 'var(--vidrio-celda)', borderRadius: 16, padding: '10px 12px', display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>{TIPO[t] ?? t}</span>
                      <span className="n" style={{ fontSize: 18, fontWeight: 800 }}>{r.resumen.por_tipo[t]}</span>
                      <span className="n" style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>S/ {n(parcialDelTipo(t), 2)}</span>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              <>
                <TituloSeccion>Así funciona</TituloSeccion>
                <p style={{ fontSize: 14, color: 'var(--tinta-2)', lineHeight: 1.55 }}>
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
            <p style={{ fontSize: 13, color: 'var(--tinta-2)', margin: '4px 0 8px' }}>Se informan, no se descartan en silencio.</p>
            {r.fallidos.map((f, i) => (
              <div key={i} style={{ fontSize: 13, color: 'var(--tinta-2)', padding: '4px 0' }}>
                <span className="n">{f.texto}</span> — {f.error}
              </div>
            ))}
          </Vidrio>
        )}

        {r && (
          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
              <TituloSeccion>Lo que se va a importar</TituloSeccion>
              <div role="tablist" aria-label="Tipo de insumo" style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {tipos.map((t) => {
                  const activo = t === tipoVisto
                  return (
                    <button
                      key={t} type="button" role="tab" aria-selected={activo} className="foco-vidrio"
                      onClick={() => setPestana(t)}
                      style={{
                        minHeight: 40, padding: '0 14px', borderRadius: 20, fontFamily: 'inherit', fontSize: 13, fontWeight: 700, cursor: 'pointer',
                        border: activo ? '1px solid var(--pildora)' : '1px solid var(--pildora-2-borde)',
                        background: activo ? 'var(--pildora)' : 'var(--pildora-2)', color: activo ? 'var(--pildora-tinta)' : 'var(--pildora-2-tinta)',
                      }}
                    >
                      {TIPO[t] ?? t} · {r.resumen.por_tipo[t]}
                    </button>
                  )
                })}
              </div>
            </div>
            <div role="tabpanel" style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio">
                <thead>
                  <tr>
                    <th>Código S10</th><th>Descripción</th><th>Und.</th>
                    <th className="n">Cantidad</th><th className="n">Precio S/</th><th className="n">Parcial S/</th><th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {filasDelTipo.slice(0, 40).map((ins) => (
                    <tr key={ins.codigo}>
                      <td className="n" style={{ color: 'var(--tinta-2)', textAlign: 'left' }}>{ins.codigo}</td>
                      <td style={{ fontWeight: 600 }}>{ins.descripcion}</td>
                      <td>{ins.unidad}</td>
                      <td className="n">{n(ins.cantidad)}</td>
                      <td className="n">{n(ins.precio, 2)}</td>
                      <td className="n" style={{ fontWeight: 700 }}>{n(ins.parcial, 2)}</td>
                      <td><Chip tono={ins.accion === 'crear' ? 'bien' : 'aviso'}>{ins.accion === 'crear' ? 'Crear' : 'Actualizar'}</Chip></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>
              Vista previa: {Math.min(40, filasDelTipo.length)} de {filasDelTipo.length} · {(TIPO[tipoVisto] ?? tipoVisto).toLowerCase()}.
            </span>
          </Vidrio>
        )}

        {r && (
          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {aplicado ? (
              <div role="status" style={{ fontSize: 14 }}>
                <strong>Catálogo actualizado.</strong> {aplicado.creados} creados · {aplicado.actualizados} actualizados · {aplicado.sinCambios} sin cambios.
                {aplicado.advertencias.length > 0 && (
                  <div style={{ marginTop: 6, fontSize: 13, color: 'var(--tinta-2)' }}>
                    {aplicado.advertencias.slice(0, 5).map((w, i) => <div key={i}>• {w}</div>)}
                    {aplicado.advertencias.length > 5 && <div>… y {aplicado.advertencias.length - 5} avisos más</div>}
                  </div>
                )}
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                <span style={{ fontSize: 14, color: 'var(--tinta-2)', flex: '1 1 300px', lineHeight: 1.5 }}>
                  Al confirmar, los insumos entran al catálogo: los códigos nuevos se crean y los que ya existen se actualizan. No se toca ningún saldo.
                </span>
                <Pildora enClic={aplicar} deshabilitado={aplicando}>
                  {aplicando ? 'Aplicando…' : `Confirmar e importar ${r.resumen.leidos} insumos`}
                </Pildora>
              </div>
            )}

            {aplicado && (requerimiento ? (
              <div role="status" style={{ fontSize: 14, borderTop: '1px solid var(--separador)', paddingTop: 14 }}>
                <strong>Requerimiento de la obra cargado.</strong> {requerimiento.creados} insumos nuevos · {requerimiento.sinCambios} ya estaban
                {requerimiento.fallidos.length > 0 && ` · ${requerimiento.fallidos.length} no se cargaron`}.
                {(requerimiento.advertencias.length > 0 || requerimiento.fallidos.length > 0) && (
                  <div style={{ marginTop: 6, fontSize: 13, color: 'var(--tinta-2)' }}>
                    {requerimiento.fallidos.slice(0, 3).map((f, i) => <div key={`f${i}`}>• {f.insumoCodigo}: {f.motivo}</div>)}
                    {requerimiento.advertencias.slice(0, 3).map((w, i) => <div key={`w${i}`}>• {w}</div>)}
                  </div>
                )}
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap', justifyContent: 'flex-end', borderTop: '1px solid var(--separador)', paddingTop: 14 }}>
                <span style={{ fontSize: 14, color: 'var(--tinta-2)', flex: '1 1 300px', lineHeight: 1.5 }}>
                  Siguiente: cargar lo planificado (cantidad y precio del S10) como requerimiento de la obra activa. Es el cien por ciento contra el que se controla todo; lo ya cargado no se pisa.
                </span>
                <Pildora enClic={cargarReq} deshabilitado={cargandoReq}>{cargandoReq ? 'Cargando…' : 'Cargar requerimiento de la obra'}</Pildora>
              </div>
            ))}
            {avisoError}
          </Vidrio>
        )}

        <p style={{ fontSize: 13, color: 'var(--tinta-2)', padding: '0 4px', lineHeight: 1.5 }}>
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
    <li style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={bien ? 'var(--bien)' : 'var(--aviso)'} strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none', marginTop: 1 }}>
        {bien ? <path d="M5 12.5l4.5 4.5L19 7.5" /> : <path d="M12 8v5M12 16.5v.5" />}
      </svg>
      <span>{children}</span>
    </li>
  )
}
