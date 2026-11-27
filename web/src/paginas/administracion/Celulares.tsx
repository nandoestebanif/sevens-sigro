/* HU-15 · Celulares autorizados de la obra (regla 5: uno por responsable, hasta dos por obra).
   Autorizar un pendiente; revocar uno autorizado, siempre con motivo. Si la persona ya tiene otro
   celular autorizado, la API responde 409 y aquí se ofrece revocar el anterior y autorizar el nuevo. */
import { AccionesFila, TablaDatos } from '../../ui/TablaDatos'
import { useState } from 'react'
import { ErrorApi } from '../../api/error'
import { administracion, type Dispositivo } from '../../api/administracion'
import { Boton, Chip, Modal, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Estado, useCarga } from './comun'
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
    <div className="d-flex flex-column gap-3">
      <p className="small text-body-secondary">
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
            <div className="d-flex gap-2 flex-wrap">
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
              <TablaDatos
                etiqueta="Celulares de la obra"
                filas={filas}
                clave={(d) => d.id}
                buscar={(d) => `${d.usuarioNombre} ${d.identificador}`}
                marcadorBusqueda="Buscar por persona o código…"
                fechas={[
                  { clave: 'pedido', titulo: 'Pedido', valor: (d) => d.solicitadoEn },
                  { clave: 'autorizacion', titulo: 'Autorización', valor: (d) => d.fechaAutorizacion },
                  { clave: 'revocacion', titulo: 'Revocación', valor: (d) => d.fechaRevocacion },
                ]}
                detalle={(d) => ({
                  titulo: d.usuarioNombre,
                  subtitulo: 'Celular de la obra',
                  chips: <Chip tono={ESTADO_CELULAR[d.estado]?.tono ?? 'neutro'}>{ESTADO_CELULAR[d.estado]?.texto ?? d.estado}</Chip>,
                  secciones: [
                    { titulo: 'Celular', datos: [['Código del celular', <span key="c" className="n text-break">{d.identificador}</span>], ['Persona', d.usuarioNombre]] },
                    { titulo: 'Historial', datos: [
                      ['Pedido', fechaLima(d.solicitadoEn)],
                      ['Autorizado', d.fechaAutorizacion ? fechaLima(d.fechaAutorizacion) : 'Todavía no'],
                      ...(d.fechaRevocacion ? [['Revocado', fechaLima(d.fechaRevocacion)] as [string, string]] : []),
                      ...(d.motivoRevocacion ? [['Motivo de la revocación', d.motivoRevocacion] as [string, string]] : []),
                    ] },
                  ],
                  extra: <p className="small text-body-secondary mb-0">
                    {d.estado === 'activo'
                      ? 'Desde este celular la persona puede registrar ingresos y salidas en esta obra.'
                      : d.estado === 'pendiente'
                        ? 'Lo que registre desde este celular queda en su cola hasta que lo autorices.'
                        : 'Lo que registre desde este celular el servidor lo rechaza. Para volver a usarlo, que lo pida de nuevo desde la app.'}
                  </p>,
                })}
                vacio={{ titulo: 'Sin celulares', detalle: 'El almacenero o el SSOMA piden la autorización desde la app.' }}
                columnas={[
                  { clave: 'persona', titulo: 'Persona', valor: (d) => d.usuarioNombre, celda: (d) => <><strong>{d.usuarioNombre}</strong><div className="small text-body-secondary text-break" style={{ maxWidth: 260 }}>{d.identificador}</div></> },
                  { clave: 'estado', titulo: 'Estado', valor: (d) => ESTADO_CELULAR[d.estado]?.texto ?? d.estado, celda: (d) => <Chip tono={ESTADO_CELULAR[d.estado]?.tono ?? 'neutro'}>{ESTADO_CELULAR[d.estado]?.texto ?? d.estado}</Chip> },
                  { clave: 'fechas', titulo: 'Fechas', valor: (d) => d.solicitadoEn, celda: (d) => (
                    <div className="small text-body-secondary" style={{ lineHeight: 1.6 }}>
                      <div>Pedido: {fechaLima(d.solicitadoEn)}</div>
                      {d.fechaAutorizacion && <div>Autorizado: {fechaLima(d.fechaAutorizacion)}</div>}
                      {d.fechaRevocacion && <div>Revocado: {fechaLima(d.fechaRevocacion)}</div>}
                      {d.motivoRevocacion && <div>Motivo: {d.motivoRevocacion}</div>}
                    </div>
                  ) },
                ]}
                acciones={(d) => (
                  <AccionesFila de={d.usuarioNombre} acciones={[
                    puedeAutorizar(d) && { texto: ocupado === d.id ? 'Autorizando…' : 'Autorizar', deshabilitado: ocupado !== null, enClic: () => autorizar(d) },
                    puedeRevocar(d) && { texto: 'Revocar', peligro: true, deshabilitado: ocupado !== null, enClic: () => { setHecho(null); setAviso(null); setRevocando({ d }) } },
                  ]} />
                )}
              />
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
      <div className="d-flex flex-column gap-3">
        <p className="small text-body-secondary">
          {d.usuarioNombre} ya no podrá registrar desde el celular <span className="n">{d.identificador}</span>.
          {luegoAutorizar && ' Después se autoriza el celular nuevo.'} La revocación queda registrada con tu nombre, la fecha y el motivo.
        </p>
        <div className="d-flex flex-column gap-2">
          <label htmlFor="motivo-revocacion" className="small fw-semibold text-body-secondary">
            Motivo (obligatorio)
          </label>
          <textarea className="form-control"
            id="motivo-revocacion"
            value={motivo}
            autoFocus
            rows={3}
            maxLength={300}
            onChange={(e) => { setMotivo(e.target.value); setError(null) }}
            placeholder="Ej.: cambió de celular, se perdió el equipo"
            aria-invalid={error ? true : undefined}

          />
        </div>
        {error && <AvisoError texto={error} />}
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>
            {ocupado ? 'Guardando…' : luegoAutorizar ? 'Revocar y autorizar' : 'Revocar'}
          </Boton>
        </div>
      </div>
    </Modal>
  )
}
