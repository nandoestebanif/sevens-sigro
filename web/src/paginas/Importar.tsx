/* ─────────────────────────────────────────────────────────────────────────────
   HU-07 · Como residente de obra, quiero importar el catálogo de insumos del
           S10, para evitar cargar a mano cientos de insumos.
   (Pantalla 11 del lienzo, «Importar el catálogo del S10».)

   Criterios de la tarjeta y dónde se ven:
   - Acepta el archivo de recursos exportado del S10 → se sube el PDF real.
   - Un insumo ya existente se actualiza, no se duplica → columna «acción»
     (el interruptor «la obra ya tiene catálogo» simula a la API mandando los
     códigos que ya existen).
   - El resultado informa cuántos se crearon, actualizaron y fallaron → tarjetas.
   - Puede repetirse sin alterar los saldos → el parser no toca ninguna base y
     los saldos viven en los movimientos; repetir es volver a leer.
   El PDF lo procesa el micro de integraciones; la web solo lo sube y muestra.
   ───────────────────────────────────────────────────────────────────────── */
import { useRef, useState } from 'react'
import { api } from '../api/cliente'
import { ErrorImportacion, importarRelacionS10, type ResultadoImportacion } from '../api/s10'
import { usaApiReal } from '../api/http'
import { ErrorApi } from '../api/error'
import type { ResultadoRequerimiento } from '../api/tipos'
import { Boton, Chip, Tarjeta, Titulo, Vacio } from '../ui/base'

const n = (v: number, d = 0) => v.toLocaleString('es-PE', { minimumFractionDigits: d, maximumFractionDigits: d === 0 ? 4 : d })
const TIPO: Record<string, string> = {
  mano_obra: 'Mano de obra', material: 'Materiales', equipo: 'Equipos', subcontrato: 'Subcontratos',
}

function Cifra({ rot, valor, color }: { rot: string; valor: string; color?: string }) {
  return (
    <Tarjeta estilo={{ flex: '1 1 130px', padding: '13px 16px' }}>
      <div style={{ fontSize: 11.5, color: 'var(--tinta-3)', fontWeight: 600 }}>{rot}</div>
      <div className="n" style={{ fontSize: 23, fontWeight: 700, marginTop: 5, color: color ?? 'var(--tinta)' }}>{valor}</div>
    </Tarjeta>
  )
}

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
  const entrada = useRef<HTMLInputElement>(null)

  const importar = async () => {
    if (!archivo) return
    setSubiendo(true)
    setError('')
    setResultado(null)
    setAplicado(null)
    setRequerimiento(null)
    try {
      // «la obra ya tiene catálogo»: la API mandaría los códigos que ya tiene;
      // aquí se simula con la relación actual de la obra
      // con la API real, los códigos que ya existen los dice el catálogo del servidor
      const existentes = conCatalogo || usaApiReal ? (await api.listarInsumos()).map((i) => i.codigo) : []
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

  return (
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <Titulo sub="Es el paso que sigue a crear la obra (HU-01): su Relación de insumos se sube una vez y la relación de la obra queda cargada. Se repite solo si el presupuesto del S10 cambia — y repetirla actualiza, no duplica.">
        Importar del S10
      </Titulo>

      <Tarjeta>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, alignItems: 'center' }}>
          <input
            ref={entrada}
            type="file"
            accept="application/pdf"
            style={{ display: 'none' }}
            onChange={(e) => { setArchivo(e.target.files?.[0] ?? null); setResultado(null); setError('') }}
          />
          <Boton tipo="secundario" enClic={() => entrada.current?.click()}>
            {archivo ? 'Cambiar archivo' : 'Elegir el PDF del S10'}
          </Boton>
          <span style={{ fontSize: 13, color: archivo ? 'var(--tinta)' : 'var(--tinta-3)', fontWeight: archivo ? 600 : 400, flex: '1 1 200px', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {archivo ? archivo.name : 'Relación de insumos (precios y cantidades de recursos requeridos por tipo)'}
          </span>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--tinta-2)', cursor: 'pointer' }}>
            <input type="checkbox" checked={conCatalogo} onChange={(e) => setConCatalogo(e.target.checked)} style={{ accentColor: 'var(--accion)' }} />
            la obra ya tiene catálogo (los repetidos se actualizan)
          </label>
          <Boton enClic={importar} deshabilitado={!archivo || subiendo}>
            {subiendo ? 'Leyendo el archivo…' : 'Importar'}
          </Boton>
        </div>
        {error && <p role="alert" style={{ marginTop: 12, fontSize: 13, color: 'var(--grave)', fontWeight: 600 }}>{error}</p>}
      </Tarjeta>

      {!r && !error && (
        <Tarjeta>
          <Vacio
            titulo="Así funciona"
            detalle="El archivo lo procesa el servicio de integraciones: cada insumo llega con su código, unidad, cantidad y precio planificados. Un código que ya existe se actualiza (jamás se duplica), una fila ilegible se informa con su motivo, y repetir la importación no toca ningún saldo: el catálogo no es el almacén."
          />
        </Tarjeta>
      )}

      {r && (
        <>
          <Tarjeta estilo={{ padding: '12px 16px', borderLeft: '4px solid var(--bien)' }}>
            <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>
              <strong style={{ color: 'var(--tinta)' }}>{r.obra ?? 'Obra sin identificar'}</strong>
              {r.subpresupuesto ? ` · subpresupuesto ${r.subpresupuesto}` : ''}{r.fecha ? ` · ${r.fecha}` : ''}
            </span>
          </Tarjeta>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
            <Cifra rot="Insumos leídos" valor={String(r.resumen.leidos)} />
            <Cifra rot="A crear" valor={String(r.resumen.a_crear)} color="var(--bien)" />
            <Cifra rot="A actualizar" valor={String(r.resumen.a_actualizar)} color="var(--aviso)" />
            <Cifra rot="Fallidos" valor={String(r.resumen.fallidos)} color={r.resumen.fallidos ? 'var(--grave)' : undefined} />
            <Cifra rot="Total planificado" valor={`S/ ${n(r.resumen.parcial_total, 2)}`} />
          </div>

          <Tarjeta estilo={{ padding: '12px 16px', borderLeft: `4px solid ${aplicado ? 'var(--bien)' : 'var(--accion)'}` }}>
            {aplicado ? (
              <div role="status" style={{ fontSize: 13 }}>
                <strong>Catálogo actualizado.</strong> {aplicado.creados} creados · {aplicado.actualizados} actualizados · {aplicado.sinCambios} sin cambios.
                {aplicado.advertencias.length > 0 && (
                  <div style={{ marginTop: 6, fontSize: 12, color: 'var(--tinta-2)' }}>
                    {aplicado.advertencias.slice(0, 5).map((w, i) => <div key={i}>• {w}</div>)}
                    {aplicado.advertencias.length > 5 && <div>… y {aplicado.advertencias.length - 5} avisos más</div>}
                  </div>
                )}
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                <span style={{ fontSize: 13, flex: '1 1 260px' }}>
                  Revisa la vista previa. Al aplicar, los insumos entran al catálogo: los códigos nuevos se crean y los que ya existen se actualizan. No se toca ningún saldo.
                </span>
                <Boton enClic={aplicar} deshabilitado={aplicando}>{aplicando ? 'Aplicando…' : 'Aplicar al catálogo'}</Boton>
              </div>
            )}
          </Tarjeta>

          {aplicado && (
            <Tarjeta estilo={{ padding: '12px 16px', borderLeft: `4px solid ${requerimiento ? 'var(--bien)' : 'var(--accion)'}` }}>
              {requerimiento ? (
                <div role="status" style={{ fontSize: 13 }}>
                  <strong>Requerimiento de la obra cargado.</strong> {requerimiento.creados} insumos nuevos · {requerimiento.sinCambios} ya estaban
                  {requerimiento.fallidos.length > 0 && ` · ${requerimiento.fallidos.length} no se cargaron`}.
                  {(requerimiento.advertencias.length > 0 || requerimiento.fallidos.length > 0) && (
                    <div style={{ marginTop: 6, fontSize: 12, color: 'var(--tinta-2)' }}>
                      {requerimiento.fallidos.slice(0, 3).map((f, i) => <div key={`f${i}`}>• {f.insumoCodigo}: {f.motivo}</div>)}
                      {requerimiento.advertencias.slice(0, 3).map((w, i) => <div key={`w${i}`}>• {w}</div>)}
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 13, flex: '1 1 260px' }}>
                    Paso 2 · Cargar lo planificado (cantidad y precio del S10) como requerimiento de la obra activa. Es el cien por ciento contra el que se controla todo; lo ya cargado no se pisa.
                  </span>
                  <Boton enClic={cargarReq} deshabilitado={cargandoReq}>{cargandoReq ? 'Cargando…' : 'Cargar requerimiento de la obra'}</Boton>
                </div>
              )}
            </Tarjeta>
          )}

          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {Object.entries(r.resumen.por_tipo).map(([t, c]) => (
              <Chip key={t} tono="neutro">{TIPO[t] ?? t}: {c}</Chip>
            ))}
          </div>

          {r.fallidos.length > 0 && (
            <Tarjeta estilo={{ padding: '12px 16px', borderLeft: '4px solid var(--grave)' }}>
              <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Filas que no se entendieron (se informan, no se descartan en silencio)</div>
              {r.fallidos.map((f, i) => (
                <div key={i} style={{ fontSize: 12, color: 'var(--tinta-2)', padding: '4px 0' }}>
                  <span className="n">{f.texto}</span> — {f.error}
                </div>
              ))}
            </Tarjeta>
          )}

          <Tarjeta relleno={false}>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--borde)', fontSize: 13.5, fontWeight: 700 }}>
              Vista previa · primeros 40 de {r.resumen.leidos}
            </div>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                  {['Código', 'Insumo', 'Tipo', 'Unidad', 'Cantidad', 'Precio S/', 'Parcial S/', 'Acción'].map((h, i) => (
                    <th key={h} style={{ padding: '10px 14px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: i >= 4 ? 'right' : 'left' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {r.insumos.slice(0, 40).map((ins, i) => (
                  <tr key={ins.codigo} style={{ borderBottom: i === 39 ? 'none' : '1px solid var(--linea)' }}>
                    <td className="n" style={{ padding: '9px 14px', fontSize: 12, color: 'var(--tinta-3)' }}>{ins.codigo}</td>
                    <td style={{ padding: '9px 14px', fontSize: 12.5, fontWeight: 600 }}>{ins.descripcion}</td>
                    <td style={{ padding: '9px 14px', fontSize: 12, color: 'var(--tinta-2)' }}>{TIPO[ins.tipo]}</td>
                    <td style={{ padding: '9px 14px', fontSize: 12, color: 'var(--tinta-2)' }}>{ins.unidad}</td>
                    <td className="n" style={{ padding: '9px 14px', fontSize: 12.5, textAlign: 'right' }}>{n(ins.cantidad)}</td>
                    <td className="n" style={{ padding: '9px 14px', fontSize: 12.5, textAlign: 'right' }}>{n(ins.precio, 2)}</td>
                    <td className="n" style={{ padding: '9px 14px', fontSize: 12.5, fontWeight: 700, textAlign: 'right' }}>{n(ins.parcial, 2)}</td>
                    <td style={{ padding: '9px 14px' }}>
                      <Chip tono={ins.accion === 'crear' ? 'bien' : 'aviso'}>{ins.accion}</Chip>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Tarjeta>
        </>
      )}

      <p style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
        El emparejamiento usa el <strong>código del insumo como clave</strong>: por eso
        repetir la importación actualiza en vez de duplicar. Y nunca toca los saldos,
        porque el catálogo no es el almacén — el stock vive en los movimientos.
      </p>
    </div>
  )
}
