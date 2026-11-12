/* ─────────────────────────────────────────────────────────────────────────────
   HU-43 · Como residente de obra, quiero consultar el stock de cada insumo en
           cualquier momento, para programar la jornada sabiendo con qué cuento.
   HU-45 · Como almacenero, quiero corregir un movimiento sin borrar el
           original, para conservar el rastro de lo registrado y lo corregido.
   Lienzo W7 «Ingresos y salidas» (estilo vidrio): stock y kárdex con reverso.

   Criterios de HU-43:
   - Saldo por almacén y consolidado por proyecto.
   - Fecha y hora del último movimiento considerado (la hora del servidor).
   - Señala los insumos por debajo de su punto de reposición.
   Criterios de HU-45:
   - La corrección genera un movimiento de reverso que apunta al original.
   - El original nunca se modifica ni se elimina (anuladoPor se deriva al leer).
   - El saldo se recalcula considerando ambos movimientos (lo hace la capa API).
   - La consulta permite ver el movimiento anulado y el que lo anuló.
   ───────────────────────────────────────────────────────────────────────── */
import { usaApiReal } from '../api/http'
import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { Movimiento, StockInsumo } from '../api/tipos'
import { Chip, Modal, Vacio } from '../ui/base'
import { Aviso, Chips, Encabezado, Fondo, Pildora, TituloSeccion, Vidrio } from '../ui/vidrio'

const n = (v: number) => v.toLocaleString('es-PE')
const hora = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)} ${iso.slice(11, 16)}`

const TIPO: Record<Movimiento['tipo'], { texto: string; signo: string }> = {
  ingreso: { texto: 'Ingreso', signo: '+' },
  salida: { texto: 'Salida', signo: '−' },
  reverso: { texto: 'Reverso', signo: '↩' },
}

function Reverso({ movimiento, enCerrar, enListo }: {
  movimiento: Movimiento
  enCerrar: () => void
  enListo: () => void
}) {
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  const corregir = async () => {
    setGuardando(true)
    setError('')
    try {
      await api.revertirMovimiento(movimiento.id, motivo)
      enListo()
    } catch (e) {
      if (e instanceof ErrorApi) setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Corregir ${movimiento.id} con un reverso`} enCerrar={enCerrar}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ padding: '12px 14px', background: 'var(--vidrio-celda)', borderRadius: 16 }}>
          <div style={{ fontSize: 13.5, color: 'var(--tinta-2)', lineHeight: 1.6 }}>
            <strong style={{ color: 'var(--tinta)' }}>{TIPO[movimiento.tipo].texto}</strong>{' '}
            · {movimiento.descripcion}<br />
            {n(movimiento.cantidad)} {movimiento.unidad} · S/ {movimiento.costoUnitario.toFixed(2)} c/u ·{' '}
            {movimiento.almacen} · {hora(movimiento.fechaServidor)}
          </div>
        </div>
        <div>
          <label htmlFor="rev-motivo" style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)', marginBottom: 6 }}>
            Motivo de la corrección (obligatorio)
          </label>
          <textarea
            id="rev-motivo"
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="p. ej. Se digitó 12; el conteo real fue 2"
            rows={2}
            className="campo"
            style={{ height: 'auto', padding: '10px 14px', resize: 'vertical', borderColor: error ? 'var(--grave)' : undefined }}
          />
          {error && <div role="alert" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--grave)', marginTop: 5 }}>{error}</div>}
        </div>
        <p style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
          El original <strong>no se toca</strong>: queda en el libro con la marca de quién
          lo anuló. El reverso hereda la cantidad y el costo congelado, y el saldo se
          recalcula con ambos. Si además corresponde otra cantidad, se registra el
          movimiento correcto aparte.
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, justifyContent: 'flex-end' }}>
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={corregir} deshabilitado={guardando}>
            {guardando ? 'Registrando…' : 'Registrar el reverso'}
          </Pildora>
        </div>
      </div>
    </Modal>
  )
}

export function Movimientos() {
  const [stock, setStock] = useState<StockInsumo[] | null>(null)
  const [kardex, setKardex] = useState<Movimiento[] | null>(null)
  const [corrigiendo, setCorrigiendo] = useState<Movimiento | null>(null)
  const [aviso, setAviso] = useState('')

  const cargar = () => {
    api.consultarStock().then(setStock)
    api.listarMovimientos().then(setKardex)
  }
  useEffect(() => {
    cargar()
  }, [])

  const corte = kardex?.[0]?.fechaServidor

  return (
    <>
    <Fondo dibujo="movimientos" />
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Encabezado
        titulo="Ingresos y salidas"
        sub={`El movimiento es un libro que no se borra: corregir es registrar un reverso. ${corte ? `Último movimiento considerado: ${hora(corte)} (hora del servidor).` : ''}`}
      />

      <Chips>
        {usaApiReal
          ? <Chip tono="bien">Movimientos y stock: del servidor</Chip>
          : <Chip tono="info">Movimientos y stock: datos de ejemplo · insumos del S10 real</Chip>}
        <Chip tono="neutro">El saldo lo calcula el servidor</Chip>
        <Chip tono="neutro">Costo promedio ponderado (NIC 2)</Chip>
      </Chips>

      {/* ── HU-43 · stock por almacén y consolidado ── */}
      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Stock al momento">
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
          <TituloSeccion>Stock al momento</TituloSeccion>
          <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>el saldo se deriva de los movimientos: no es un campo editable</span>
        </div>
        {stock === null ? (
          <Vacio titulo="Cargando…" detalle="Sumando los movimientos del libro." />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="tabla-vidrio">
              <thead>
                <tr>
                  <th>Insumo</th><th>Por almacén</th><th className="n">Consolidado</th><th className="n">Punto de reposición</th><th className="n">Último movimiento</th>
                </tr>
              </thead>
              <tbody>
                {stock.map((s) => (
                  <tr key={s.insumoId}>
                    <td style={{ minWidth: 240 }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', fontWeight: 600 }}>
                        {s.descripcion}
                        {s.bajoPunto && <Chip tono="grave">bajo el punto</Chip>}
                      </span>
                      <div className="n" style={{ fontSize: 12, color: 'var(--tinta-2)', marginTop: 2, textAlign: 'left' }}>SKU {s.insumoId}</div>
                    </td>
                    <td style={{ color: 'var(--tinta-2)' }}>
                      {s.porAlmacen.map((a) => (
                        <div key={a.almacen} className="n" style={{ textAlign: 'left', whiteSpace: 'nowrap' }}>{a.almacen}: <strong style={{ color: 'var(--tinta)' }}>{n(a.saldo)}</strong> {s.unidad}</div>
                      ))}
                    </td>
                    <td className="n" style={{ fontWeight: 800, whiteSpace: 'nowrap' }}>{n(s.saldoTotal)} {s.unidad}</td>
                    <td className="n" style={{ color: s.bajoPunto ? 'var(--grave)' : 'var(--tinta-2)', fontWeight: s.bajoPunto ? 700 : 400, whiteSpace: 'nowrap' }}>
                      {s.puntoReposicion === null ? '—' : `${n(s.puntoReposicion)} ${s.unidad}`}
                    </td>
                    <td className="n" style={{ color: 'var(--tinta-2)', whiteSpace: 'nowrap' }}>{hora(s.ultimoMovimiento)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Vidrio>

      {aviso && <Aviso tono="bien" rol="status">{aviso}</Aviso>}

      {/* ── HU-45 · kárdex con reverso ── */}
      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Kárdex">
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
          <TituloSeccion>Kárdex · los movimientos del libro</TituloSeccion>
          <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>aquí no hay editar ni eliminar: solo corregir con reverso</span>
        </div>
        {kardex === null ? (
          <Vacio titulo="Cargando…" detalle="Abriendo el libro de movimientos." />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="tabla-vidrio" style={{ minWidth: 860 }}>
              <thead>
                <tr>
                  <th>Mov.</th><th>Fecha (servidor)</th><th>Tipo</th><th>Insumo y motivo</th>
                  <th className="n">Cantidad</th><th className="n">Costo cong.</th><th aria-label="Acción" />
                </tr>
              </thead>
              <tbody>
                {kardex.map((m) => {
                  const anulado = m.anuladoPor !== null
                  return (
                    <tr key={m.id} style={{ background: m.tipo === 'reverso' ? 'var(--aviso-tinte)' : undefined }}>
                      <td className="n" style={{ color: 'var(--tinta-2)', whiteSpace: 'nowrap', textAlign: 'left' }}>{m.id}</td>
                      <td className="n" style={{ color: 'var(--tinta-2)', whiteSpace: 'nowrap', textAlign: 'left' }}>{hora(m.fechaServidor)}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <Chip tono={m.tipo === 'ingreso' ? 'bien' : m.tipo === 'salida' ? 'marca' : 'aviso'}>
                          {TIPO[m.tipo].texto}
                        </Chip>
                      </td>
                      <td style={{ minWidth: 260 }}>
                        <div style={{ fontWeight: 600, textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-2)' : 'var(--tinta)' }}>
                          {m.descripcion}
                        </div>
                        <div style={{ fontSize: 12.5, color: 'var(--tinta-2)', marginTop: 2 }}>
                          {m.motivo} · {m.almacen} · {m.autor}
                          {m.reversaA && <strong> · anula a {m.reversaA}</strong>}
                          {anulado && <strong style={{ color: 'var(--aviso)' }}> · anulado por {m.anuladoPor}</strong>}
                        </div>
                      </td>
                      <td className="n" style={{ fontWeight: 700, whiteSpace: 'nowrap', textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-2)' : m.tipo === 'ingreso' ? 'var(--bien)' : m.tipo === 'salida' ? 'var(--grave)' : 'var(--tinta)' }}>
                        {TIPO[m.tipo].signo} {n(m.cantidad)} {m.unidad}
                      </td>
                      <td className="n" style={{ whiteSpace: 'nowrap' }}>S/ {m.costoUnitario.toFixed(2)}</td>
                      <td style={{ textAlign: 'right' }}>
                        {m.tipo !== 'reverso' && !anulado && (
                          <Pildora tipo="secundario" chica enClic={() => setCorrigiendo(m)}>Corregir</Pildora>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>Cada salida congela el promedio de ese momento.</span>
      </Vidrio>

      <p style={{ fontSize: 13, color: 'var(--tinta-plano)', padding: '0 4px', lineHeight: 1.5 }}>
        El registro de ingresos y salidas en obra lo hace el almacenero desde el celular
        (con o sin señal); esta pantalla es la consulta de oficina. El costo de cada salida
        queda <strong>congelado</strong> al promedio vigente en ese instante (NIC 2): un
        ingreso posterior no lo cambia.
      </p>

      {corrigiendo && (
        <Reverso
          movimiento={corrigiendo}
          enCerrar={() => setCorrigiendo(null)}
          enListo={() => {
            setAviso(`Reverso registrado: ${corrigiendo.id} queda en el libro, anulado, y el saldo ya se recalculó con ambos movimientos.`)
            setCorrigiendo(null)
            cargar()
          }}
        />
      )}
    </div>
    </>
  )
}
