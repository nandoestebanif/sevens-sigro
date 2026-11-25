/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · kárdex de un insumo en la obra activa (HU-43, fase 2 del rediseño)

   Cada movimiento con su signo, el almacén, la semana, quién lo registró y el
   saldo que dejó. El anulado se ve tachado y el reverso lo dice: nada se borra
   (regla 10). Lo calcula el servidor; aquí solo se filtra por insumo y se pagina.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/cliente'
import { mensajeDeCarga } from '../api/error'
import type { Movimiento } from '../api/tipos'
import { Chip } from './base'
import { EsqueletoTabla } from './Esqueleto'
import { paginar } from './tablas'

const n = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 3 })
const fecha = (iso: string) => new Date(iso).toLocaleString('es-PE', { timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
const POR_PAGINA = 8

export function Kardex({ codigo }: { codigo: string }) {
  const [movs, setMovs] = useState<Movimiento[] | null>(null)
  const [error, setError] = useState('')
  const [pagina, setPagina] = useState(1)
  useEffect(() => {
    api.listarMovimientos().then(setMovs).catch((x) => { setMovs([]); setError(mensajeDeCarga(x)) })
  }, [])
  const propios = useMemo(() => (movs ?? []).filter((m) => m.insumoId === codigo), [movs, codigo])
  const p = paginar(propios, pagina, POR_PAGINA)
  const entradas = propios.filter((m) => m.tipo === 'ingreso' && !m.anuladoPor).reduce((s, m) => s + m.cantidad, 0)
  const salidas = propios.filter((m) => m.tipo === 'salida' && !m.anuladoPor).reduce((s, m) => s + m.cantidad, 0)

  return (
    <section>
      <h3 className="sigro-detalle-seccion">Kárdex en esta obra</h3>
      {movs === null ? <EsqueletoTabla filas={4} /> : error ? (
        <p className="small text-danger mb-0">{error}</p>
      ) : propios.length === 0 ? (
        <p className="small text-body-secondary mb-0">Este insumo todavía no tiene ingresos ni salidas en la obra.</p>
      ) : (
        <>
          <div className="d-flex flex-wrap gap-2 mb-2">
            <Chip tono="bien">+ {n(entradas)} {propios[0].unidad} entraron</Chip>
            <Chip tono="aviso">− {n(salidas)} {propios[0].unidad} salieron</Chip>
            <Chip tono="neutro">{propios.length} movimientos</Chip>
          </div>
          <div className="overflow-x-auto position-relative">
            <table className="table table-sm align-middle mb-0 small">
              <thead><tr><th>Fecha</th><th>Movimiento</th><th className="n">Cantidad</th><th className="n">Saldo</th></tr></thead>
              <tbody>
                {p.items.map((m) => (
                  <tr key={m.id} className={m.anuladoPor ? 'text-body-tertiary' : undefined}>
                    <td className="text-nowrap">{fecha(m.fechaServidor)}<div className="text-body-secondary">{m.semana ? `S${m.semana} · ` : ''}{m.almacen}</div></td>
                    <td>
                      <span className={m.anuladoPor ? 'text-decoration-line-through' : undefined}>
                        {m.tipo === 'ingreso' ? 'Ingreso' : m.tipo === 'salida' ? 'Salida' : 'Reverso'}
                      </span>
                      {m.anuladoPor && <> <Chip tono="neutro">anulado</Chip></>}
                      <div className="text-body-secondary text-truncate" style={{ maxWidth: 220 }} title={m.motivo}>{m.autor}{m.motivo ? ` · ${m.motivo}` : ''}</div>
                    </td>
                    <td className={`n fw-semibold ${m.tipo === 'ingreso' ? 'text-success' : ''}`}>{m.tipo === 'salida' ? '−' : '+'} {n(m.cantidad)}</td>
                    <td className="n">{m.saldoDespues !== undefined ? n(m.saldoDespues) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {p.paginas > 1 && (
            <nav aria-label="Páginas del kárdex" className="d-flex justify-content-between align-items-center mt-2">
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={p.pagina === 1} onClick={() => setPagina(p.pagina - 1)}>‹ Más recientes</button>
              <span className="small text-body-secondary n">{p.desde}–{p.hasta} de {p.total}</span>
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={p.pagina === p.paginas} onClick={() => setPagina(p.pagina + 1)}>Más antiguos ›</button>
            </nav>
          )}
        </>
      )}
    </section>
  )
}
