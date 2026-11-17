/* ─────────────────────────────────────────────────────────────────────────────
   HU-44 · Como almacenero, quiero revisar las entradas y salidas agrupadas por
           semana, para cuadrar el almacén con el corte que ya usamos.

   Criterios de la tarjeta y dónde se cumplen:
   - Se elige la semana a revisar → selector con las semanas de la obra (HU-03).
   - Muestra saldo inicial, entradas, salidas y saldo final por insumo → lo
     calcula el servidor desde el libro; aquí solo se pinta.
   - Muestra únicamente los insumos con movimiento en esa semana → filtro del
     servidor; la pantalla no oculta ni suma nada.
   - Puede exportarse → «Descargar CSV» (lo arma el servidor, mismas cifras).
   Diseño: T-308 (reporte semanal), con el estilo vidrio de «Ingresos y salidas».
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import { sesionActual, usaApiReal } from '../api/http'
import { plazo } from '../api/oficina'
import type { ReporteSemana } from '../api/tipos'
import { Chip, Vacio } from '../ui/base'
import { Aviso, Chips, Encabezado, Fondo, Pildora, TituloSeccion, Vidrio } from '../ui/vidrio'

const n = (x: number) => x.toLocaleString('es-PE', { maximumFractionDigits: 4 })
const dia = (iso: string) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : '')

export function Semana() {
  const [obraId, setObraId] = useState('')
  const [total, setTotal] = useState(0)
  const [numero, setNumero] = useState<number | null>(null)
  const [r, setR] = useState<ReporteSemana | null>(null)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [bajando, setBajando] = useState(false)
  const [cerrando, setCerrando] = useState(false)
  const [confirmarCierre, setConfirmarCierre] = useState(false)

  const rol = sesionActual()?.obras.find((x) => String(x.id) === obraId)?.rol ?? sesionActual()?.obras[0]?.rol
  const puedeCerrar = usaApiReal && (rol === 'residente' || rol === 'administrador')

  // por defecto, la semana en curso de la obra activa
  useEffect(() => {
    api.obraActiva().then((o) => {
      setObraId(o.id)
      setTotal(o.semanasTotales)
      setNumero(Math.max(1, Math.min(o.semana || 1, o.semanasTotales || 1)))
    })
  }, [])

  useEffect(() => {
    if (numero === null) return
    setR(null); setError(''); setAviso(''); setConfirmarCierre(false)
    api.reporteSemana(numero).then(setR).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar la semana.'))
  }, [numero])

  const cerrar = async () => {
    if (!obraId || numero === null) return
    setCerrando(true); setError(''); setAviso('')
    try {
      await plazo.cerrarSemana(obraId, numero)
      setConfirmarCierre(false)
      setAviso(`Semana ${numero} cerrada. Desde ahora solo admite correcciones con reverso.`)
      const actualizado = await api.reporteSemana(numero)
      setR(actualizado)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo cerrar la semana.')
    } finally { setCerrando(false) }
  }

  const descargar = async () => {
    if (numero === null) return
    setBajando(true); setError('')
    try {
      const b = await api.exportarSemana(numero)
      if (!b) { setError('La exportación necesita el servidor (VITE_API_URL).'); return }
      const url = URL.createObjectURL(b)
      const a = document.createElement('a')
      a.href = url; a.download = `SIGRO-semana-${numero}.csv`; a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo descargar el archivo.')
    } finally { setBajando(false) }
  }

  return (
    <>
      <Fondo dibujo="movimientos" />
      <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Encabezado
          titulo="Movimiento semanal"
          sub="Entradas y salidas de una semana de la obra, por insumo, para cuadrar el almacén con el corte que ya se usa."
          acciones={
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {puedeCerrar && r && !r.cerrada && !confirmarCierre && (
                <Pildora tipo="secundario" enClic={() => setConfirmarCierre(true)}>Cerrar semana {numero}</Pildora>
              )}
              {puedeCerrar && r && !r.cerrada && confirmarCierre && (
                <>
                  <Pildora tipo="primario" enClic={cerrar} deshabilitado={cerrando}>{cerrando ? 'Cerrando…' : `Confirmar cierre S${numero}`}</Pildora>
                  <Pildora tipo="secundario" enClic={() => setConfirmarCierre(false)} deshabilitado={cerrando}>Cancelar</Pildora>
                </>
              )}
              <Pildora tipo="secundario" enClic={descargar} deshabilitado={bajando || !r}>{bajando ? 'Descargando…' : 'Descargar CSV'}</Pildora>
            </div>
          }
        />
        <Chips>
          <Chip tono="neutro">Lo calcula el servidor desde el libro</Chip>
          <Chip tono="neutro">Solo insumos con movimiento en la semana</Chip>
          {r?.cerrada && <Chip tono="aviso">Semana cerrada: solo admite reversos</Chip>}
        </Chips>
        {aviso && <Aviso tono="bien" rol="status">{aviso}</Aviso>}

        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Reporte de la semana">
          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
            <TituloSeccion>
              Semana {numero ?? '…'}{r?.inicio ? ` · ${dia(r.inicio)} al ${dia(r.fin)}` : ''}
            </TituloSeccion>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)' }}>
              Semana
              <select
                aria-label="Semana a revisar"
                className="campo"
                value={numero ?? ''}
                onChange={(e) => setNumero(Number(e.target.value))}
                style={{ height: 40, padding: '0 10px', fontSize: 14, borderRadius: 12 }}
              >
                {Array.from({ length: total }, (_, i) => i + 1).map((k) => <option key={k} value={k}>{k}</option>)}
              </select>
            </label>
          </div>

          {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
          {!r && !error && <Vacio titulo="Cargando…" detalle="Sumando los movimientos de la semana." />}
          {r && r.filas.length === 0 && <Vacio titulo="Sin movimientos esta semana" detalle={r.criterio} />}
          {r && r.filas.length > 0 && (
            <div style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio">
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left' }}>Insumo</th>
                    <th style={{ textAlign: 'left' }}>Almacén</th>
                    <th className="n">Saldo inicial</th><th className="n">Entradas</th><th className="n">Salidas</th><th className="n">Saldo final</th>
                  </tr>
                </thead>
                <tbody>
                  {r.filas.map((f) => (
                    <tr key={`${f.almacen}-${f.insumoId}`}>
                      <td><strong>{f.descripcion}</strong><div style={{ fontSize: 12, color: 'var(--tinta-2)' }}>SKU {f.insumoId} · {f.unidad}</div></td>
                      <td>{f.almacen}</td>
                      <td className="n">{n(f.saldoInicial)}</td>
                      <td className="n" style={{ color: 'var(--bien)' }}>{f.entradas >= 0 ? `+ ${n(f.entradas)}` : <span title="Un reverso anuló entradas de otra semana">− {n(-f.entradas)} por reverso</span>}</td>
                      <td className="n">{f.salidas >= 0 ? `− ${n(f.salidas)}` : <span title="Un reverso anuló salidas de otra semana">+ {n(-f.salidas)} por reverso</span>}</td>
                      <td className="n"><strong>{n(f.saldoFinal)}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {r && <p style={{ fontSize: 12, color: 'var(--tinta-2)', lineHeight: 1.5 }}>{r.criterio}</p>}
        </Vidrio>
      </div>
    </>
  )
}
