/* HU-15 · Celulares autorizados de la obra (regla 5: uno por responsable, hasta dos por obra).
   Autorizar un pendiente; revocar uno autorizado, siempre con motivo. Si la persona ya tiene otro
   celular autorizado, la API responde 409 y aquí se ofrece revocar el anterior y autorizar el nuevo. */
import { useState } from 'react'
import { ErrorApi } from '../../api/error'
import { administracion, type Dispositivo } from '../../api/administracion'
import { Boton, Chip, Modal, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Estado, Tabla, estiloCelda, estiloCeldaSuave, filaTabla, useCarga } from './comun'
import {
  CODIGO_YA_TIENE_CELULAR, ESTADO_CELULAR, celularAnterior, fechaLima, mensajeParaPersona, ordenarCelulares,
  puedeAutorizar, puedeRevocar, validarMotivo,
} from './reglas'

interface Conflicto { nuevo: Dispositivo; anterior: Dispositivo | null }

export function Celulares({ obraId }: { obraId: string }) {
  const { datos, error, recargar } = useCarga(() => administracion.dispositivos(obraId), [obraId])
  const [ocupado, setOcupado] = useState<number | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)
  const [conflicto, setConflicto] = useState<Conflicto | null>(null)
  const [revocando, setRevocando] = useState<{ d: Dispositivo; luegoAutorizar?: Dispositivo } | null>(null)

  const autorizar = async (d: Dispositivo) => {
    setOcupado(d.id); setAviso(null); setHecho(null); setConflicto(null)
    try {
      await administracion.autorizar(obraId, d.id)
      setHecho(`Se autorizó el celular de ${d.usuarioNombre}.`)
      await recargar()
    } catch (e) {
      if (e instanceof ErrorApi && e.codigo === CODIGO_YA_TIENE_CELULAR) {
        setConflicto({ nuevo: d, anterior: celularAnterior(datos ?? [], d) })
      } else {
        setAviso(mensajeParaPersona(e))
      }
    } finally {
      setOcupado(null)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <p style={{ fontSize: 13, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
        Solo el almacenero y el ingeniero SSOMA asignados registran desde su celular, uno por persona y hasta dos por
        obra. El servidor rechaza los registros de un celular no autorizado.
      </p>
      {hecho && <AvisoBien texto={hecho} />}
      {aviso && <AvisoError texto={aviso} />}
      {conflicto && (
        <AvisoError texto={
          `${conflicto.nuevo.usuarioNombre} ya tiene otro celular autorizado en esta obra` +
          (conflicto.anterior ? ` (código ${conflicto.anterior.identificador}).` : '.') +
          ' Solo puede tener uno: para autorizar este, primero revoca el anterior.'
        }>
          {conflicto.anterior && (
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <Boton enClic={() => { setRevocando({ d: conflicto.anterior!, luegoAutorizar: conflicto.nuevo }); setConflicto(null) }}>
                Revocar el anterior y autorizar este
              </Boton>
              <Boton tipo="secundario" enClic={() => setConflicto(null)}>Dejarlo así</Boton>
            </div>
          )}
        </AvisoError>
      )}
      <Tarjeta relleno={false}>
        <Estado
          datos={datos} error={error} recargar={recargar}
          vacio={{ titulo: 'Ningún celular pidió autorización', detalle: 'Cuando el almacenero o el SSOMA lo pidan desde la app, aparecerá aquí.' }}
        >
          {(lista) => {
            const filas = ordenarCelulares(lista)
            return (
              <Tabla etiqueta="Celulares de la obra" titulos={['Persona', 'Código del celular', 'Estado', 'Fechas', '']}>
                {filas.map((d, i) => (
                  <tr key={d.id} style={filaTabla(i === filas.length - 1)}>
                    <td style={{ ...estiloCelda, fontWeight: 600 }}>{d.usuarioNombre}</td>
                    <td className="n" style={{ ...estiloCeldaSuave, wordBreak: 'break-all', maxWidth: 260 }}>{d.identificador}</td>
                    <td style={estiloCelda}><Chip tono={ESTADO_CELULAR[d.estado]?.tono ?? 'neutro'}>{ESTADO_CELULAR[d.estado]?.texto ?? d.estado}</Chip></td>
                    <td className="n" style={{ ...estiloCeldaSuave, lineHeight: 1.6 }}>
                      <div>Pedido: {fechaLima(d.solicitadoEn)}</div>
                      {d.fechaAutorizacion && <div>Autorizado: {fechaLima(d.fechaAutorizacion)}</div>}
                      {d.fechaRevocacion && <div>Revocado: {fechaLima(d.fechaRevocacion)}</div>}
                      {d.motivoRevocacion && <div>Motivo: {d.motivoRevocacion}</div>}
                    </td>
                    <td style={{ ...estiloCelda, textAlign: 'right', whiteSpace: 'nowrap' }}>
                      {puedeAutorizar(d) && (
                        <Boton enClic={() => autorizar(d)} deshabilitado={ocupado !== null}>
                          {ocupado === d.id ? 'Autorizando…' : 'Autorizar'}
                        </Boton>
                      )}
                      {puedeRevocar(d) && (
                        <Boton tipo="secundario" enClic={() => { setHecho(null); setAviso(null); setRevocando({ d }) }} deshabilitado={ocupado !== null}>
                          Revocar
                        </Boton>
                      )}
                    </td>
                  </tr>
                ))}
              </Tabla>
            )
          }}
        </Estado>
      </Tarjeta>

      {revocando && (
        <Revocar
          d={revocando.d}
          luegoAutorizar={revocando.luegoAutorizar}
          enCerrar={() => setRevocando(null)}
          enHecho={async (texto, bien = true) => { setRevocando(null); if (bien) setHecho(texto); else setAviso(texto); await recargar() }}
          obraId={obraId}
        />
      )}
    </div>
  )
}

function Revocar({ obraId, d, luegoAutorizar, enCerrar, enHecho }: {
  obraId: string
  d: Dispositivo
  luegoAutorizar?: Dispositivo
  enCerrar: () => void
  enHecho: (texto: string, bien?: boolean) => Promise<void>
}) {
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const guardar = async () => {
    const falta = validarMotivo(motivo)
    if (falta) { setError(falta); return }
    setOcupado(true); setError(null)
    try {
      await administracion.revocar(obraId, d.id, motivo)
    } catch (e) {
      setError(mensajeParaPersona(e)); setOcupado(false); return
    }
    if (!luegoAutorizar) { await enHecho(`Se revocó el celular de ${d.usuarioNombre}.`); return }
    try {
      await administracion.autorizar(obraId, luegoAutorizar.id)
      await enHecho(`Se revocó el celular anterior y se autorizó el nuevo de ${luegoAutorizar.usuarioNombre}.`)
    } catch (e) {
      // la revocación ya quedó: se dice qué falta, sin deshacer nada
      await enHecho(`Se revocó el celular anterior, pero no se pudo autorizar el nuevo: ${mensajeParaPersona(e)}`, false)
    }
  }

  return (
    <Modal titulo={luegoAutorizar ? 'Revocar el celular anterior' : 'Revocar celular'} enCerrar={enCerrar}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <p style={{ fontSize: 13.5, lineHeight: 1.55, color: 'var(--tinta-2)' }}>
          {d.usuarioNombre} ya no podrá registrar desde el celular <span className="n">{d.identificador}</span>.
          {luegoAutorizar && ' Después se autoriza el celular nuevo.'} La revocación queda registrada con tu nombre, la fecha y el motivo.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label htmlFor="motivo-revocacion" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>
            Motivo (obligatorio)
          </label>
          <textarea
            id="motivo-revocacion"
            value={motivo}
            autoFocus
            rows={3}
            maxLength={300}
            onChange={(e) => { setMotivo(e.target.value); setError(null) }}
            placeholder="Ej.: cambió de celular, se perdió el equipo"
            aria-invalid={error ? true : undefined}
            style={{
              border: `1px solid ${error ? 'var(--grave)' : 'var(--borde)'}`, borderRadius: 'var(--radio-chico)',
              padding: '10px 13px', fontSize: 14, fontFamily: 'inherit', color: 'var(--tinta)', background: 'var(--tarjeta)', resize: 'vertical',
            }}
          />
        </div>
        {error && <AvisoError texto={error} />}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>
            {ocupado ? 'Guardando…' : luegoAutorizar ? 'Revocar y autorizar' : 'Revocar'}
          </Boton>
        </div>
      </div>
    </Modal>
  )
}
