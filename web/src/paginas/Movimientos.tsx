/* ─────────────────────────────────────────────────────────────────────────────
   HU-43 · Como residente de obra, quiero consultar el stock de cada insumo en
           cualquier momento, para programar la jornada sabiendo con qué cuento.
   HU-45 · Como almacenero, quiero corregir un movimiento sin borrar el
           original, para conservar el rastro de lo registrado y lo corregido.
   (Pantalla 15 del lienzo, «Kárdex y reverso».)

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
import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { Movimiento, StockInsumo } from '../api/tipos'
import { Boton, Chip, Modal, Tarjeta, Titulo, Vacio } from '../ui/base'

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
        <Tarjeta estilo={{ padding: '11px 14px', background: 'var(--tarjeta-suave)' }}>
          <div style={{ fontSize: 12.5, color: 'var(--tinta-2)', lineHeight: 1.6 }}>
            <strong style={{ color: 'var(--tinta)' }}>{TIPO[movimiento.tipo].texto}</strong>{' '}
            · {movimiento.descripcion}<br />
            {n(movimiento.cantidad)} {movimiento.unidad} · S/ {movimiento.costoUnitario.toFixed(2)} c/u ·{' '}
            {movimiento.almacen} · {hora(movimiento.fechaServidor)}
          </div>
        </Tarjeta>
        <div>
          <label htmlFor="rev-motivo" style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--tinta-3)', marginBottom: 6 }}>
            Motivo de la corrección (obligatorio)
          </label>
          <textarea
            id="rev-motivo"
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="p. ej. Se digitó 12; el conteo real fue 2"
            rows={2}
            style={{
              width: '100%', border: `1px solid ${error ? 'var(--grave)' : 'var(--borde)'}`,
              borderRadius: 'var(--radio-chico)', padding: '10px 12px', fontSize: 13.5,
              fontFamily: 'inherit', background: 'var(--tarjeta)', color: 'var(--tinta)',
              resize: 'vertical',
            }}
          />
          {error && <div style={{ fontSize: 12, color: 'var(--grave)', marginTop: 5 }}>{error}</div>}
        </div>
        <p style={{ fontSize: 12.5, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
          El original <strong>no se toca</strong>: queda en el libro con la marca de quién
          lo anuló. El reverso hereda la cantidad y el costo congelado, y el saldo se
          recalcula con ambos. Si además corresponde otra cantidad, se registra el
          movimiento correcto aparte.
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={corregir} deshabilitado={guardando}>
            {guardando ? 'Registrando…' : 'Registrar el reverso'}
          </Boton>
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
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <Titulo sub={`El movimiento es un libro que no se borra: corregir es registrar un reverso. ${corte ? `Último movimiento considerado: ${hora(corte)} (hora del servidor).` : ''}`}>
        Ingresos y salidas
      </Titulo>

      {/* ── HU-43 · stock por almacén y consolidado ── */}
      <Tarjeta relleno={false}>
        <div style={{ padding: '13px 16px', borderBottom: '1px solid var(--borde)', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <span style={{ fontSize: 13.5, fontWeight: 700 }}>Stock al momento</span>
          <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>el saldo se deriva de los movimientos: no es un campo editable</span>
        </div>
        {stock === null ? (
          <Vacio titulo="Cargando…" detalle="Sumando los movimientos del libro." />
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                {['Insumo', 'Por almacén', 'Consolidado', 'Punto de reposición', 'Último movimiento'].map((h, i) => (
                  <th key={h} style={{ padding: '11px 16px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: i >= 2 ? 'right' : 'left' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {stock.map((s, i) => (
                <tr key={s.insumoId} style={{ borderBottom: i === stock.length - 1 ? 'none' : '1px solid var(--linea)' }}>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600 }}>
                      {s.descripcion}
                      {s.bajoPunto && <Chip tono="grave">bajo el punto</Chip>}
                    </span>
                    <div className="n" style={{ fontSize: 11, color: 'var(--tinta-3)', marginTop: 2 }}>SKU {s.insumoId}</div>
                  </td>
                  <td style={{ padding: '12px 16px', fontSize: 12.5, color: 'var(--tinta-2)' }}>
                    {s.porAlmacen.map((a) => (
                      <div key={a.almacen} className="n">{a.almacen}: <strong>{n(a.saldo)}</strong> {s.unidad}</div>
                    ))}
                  </td>
                  <td className="n" style={{ padding: '12px 16px', fontSize: 14, fontWeight: 700, textAlign: 'right' }}>
                    {n(s.saldoTotal)} {s.unidad}
                  </td>
                  <td className="n" style={{ padding: '12px 16px', fontSize: 12.5, color: s.bajoPunto ? 'var(--grave)' : 'var(--tinta-2)', textAlign: 'right' }}>
                    {s.puntoReposicion === null ? '—' : `${n(s.puntoReposicion)} ${s.unidad}`}
                  </td>
                  <td className="n" style={{ padding: '12px 16px', fontSize: 12.5, color: 'var(--tinta-3)', textAlign: 'right' }}>
                    {hora(s.ultimoMovimiento)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Tarjeta>

      {aviso && (
        <Tarjeta estilo={{ padding: '11px 16px', borderLeft: '4px solid var(--bien)' }}>
          <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>{aviso}</span>
        </Tarjeta>
      )}

      {/* ── HU-45 · kárdex con reverso ── */}
      <Tarjeta relleno={false}>
        <div style={{ padding: '13px 16px', borderBottom: '1px solid var(--borde)', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <span style={{ fontSize: 13.5, fontWeight: 700 }}>Kárdex · los movimientos del libro</span>
          <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>aquí no hay editar ni eliminar: solo corregir con reverso</span>
        </div>
        {kardex === null ? (
          <Vacio titulo="Cargando…" detalle="Abriendo el libro de movimientos." />
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                {['Mov.', 'Fecha (servidor)', 'Tipo', 'Insumo y motivo', 'Cantidad', 'Costo cong.', ''].map((h, i) => (
                  <th key={h || 'acc'} style={{ padding: '11px 14px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: i >= 4 ? 'right' : 'left' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {kardex.map((m, i) => {
                const anulado = m.anuladoPor !== null
                return (
                  <tr
                    key={m.id}
                    style={{
                      borderBottom: i === kardex.length - 1 ? 'none' : '1px solid var(--linea)',
                      background: m.tipo === 'reverso' ? 'var(--aviso-tinte)' : 'transparent',
                    }}
                  >
                    <td className="n" style={{ padding: '11px 14px', fontSize: 12, color: 'var(--tinta-3)', whiteSpace: 'nowrap' }}>{m.id}</td>
                    <td className="n" style={{ padding: '11px 14px', fontSize: 12.5, color: 'var(--tinta-2)', whiteSpace: 'nowrap' }}>{hora(m.fechaServidor)}</td>
                    <td style={{ padding: '11px 14px', whiteSpace: 'nowrap' }}>
                      <Chip tono={m.tipo === 'ingreso' ? 'bien' : m.tipo === 'salida' ? 'neutro' : 'aviso'}>
                        {TIPO[m.tipo].texto}
                      </Chip>
                    </td>
                    <td style={{ padding: '11px 14px' }}>
                      <div style={{ fontSize: 12.5, fontWeight: 600, textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-3)' : 'var(--tinta)' }}>
                        {m.descripcion}
                      </div>
                      <div style={{ fontSize: 11.5, color: 'var(--tinta-3)', marginTop: 2 }}>
                        {m.motivo} · {m.almacen} · {m.autor}
                        {m.reversaA && <strong> · anula a {m.reversaA}</strong>}
                        {anulado && <strong style={{ color: 'var(--aviso)' }}> · anulado por {m.anuladoPor}</strong>}
                      </div>
                    </td>
                    <td className="n" style={{ padding: '11px 14px', fontSize: 13, fontWeight: 700, textAlign: 'right', whiteSpace: 'nowrap', textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-3)' : 'var(--tinta)' }}>
                      {TIPO[m.tipo].signo} {n(m.cantidad)} {m.unidad}
                    </td>
                    <td className="n" style={{ padding: '11px 14px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right', whiteSpace: 'nowrap' }}>
                      S/ {m.costoUnitario.toFixed(2)}
                    </td>
                    <td style={{ padding: '11px 14px', textAlign: 'right' }}>
                      {m.tipo !== 'reverso' && !anulado && (
                        <button
                          type="button"
                          onClick={() => setCorrigiendo(m)}
                          style={{
                            border: '1px solid var(--borde)', background: 'var(--tarjeta)',
                            color: 'var(--tinta-2)', borderRadius: 'var(--radio-pildora)',
                            padding: '5px 12px', fontSize: 12, fontWeight: 600,
                            cursor: 'pointer', fontFamily: 'inherit', whiteSpace: 'nowrap',
                          }}
                        >
                          Corregir
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Tarjeta>

      <p style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
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
  )
}
