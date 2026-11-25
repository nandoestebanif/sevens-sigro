/* HU-13 · Usuarios de la empresa. Alta con clave temporal (la regla de clave la aplica y la explica el
   servidor) y baja: nada se borra (regla 10), la persona queda «de baja» y pierde el acceso al instante. */
import { AccionesFila, TablaDatos } from '../../ui/TablaDatos'
import { useState } from 'react'
import { useEnVivo } from '../../formularios/enVivo'
import { ErrorApi } from '../../api/error'
import { administracion, type Usuario } from '../../api/administracion'
import { Boton, Campo, Chip, Modal, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Confirmar, Estado, useCarga } from './comun'
import { campoDelError, estaDeBaja, fechaLima, mensajeParaPersona, ordenarUsuarios, validarAltaUsuario } from './reglas'

export function Usuarios({ usuarioSesionId }: { usuarioSesionId: number | null }) {
  const { datos, error, recargar } = useCarga(() => administracion.usuarios(), [])
  const [alta, setAlta] = useState(false)
  const [baja, setBaja] = useState<Usuario | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)

  return (
    <div className="d-flex flex-column gap-3">
      <div className="d-flex justify-content-between align-items-center gap-3 flex-wrap">
        <p className="small text-body-secondary flex-grow-1" style={{ minWidth: 260 }}>
          Los usuarios son de la empresa; el rol de cada uno se da por obra en «Asignaciones». Una baja no borra a la
          persona ni lo que registró: solo le corta el acceso.
        </p>
        <Boton enClic={() => { setHecho(null); setAlta(true) }}>Registrar usuario</Boton>
      </div>
      {hecho && <AvisoBien texto={hecho} />}
      <Tarjeta relleno={false}>
        <Estado
          datos={datos} error={error} recargar={recargar}
          vacio={{ titulo: 'Todavía no hay usuarios', detalle: 'Registra al primero con «Registrar usuario».' }}
        >
          {(lista) => {
            const filas = ordenarUsuarios(lista)
            return (
              <TablaDatos
                etiqueta="Usuarios"
                filas={filas}
                clave={(u) => u.id}
                buscar={(u) => `${u.nombre} ${u.usuario}`}
                marcadorBusqueda="Buscar por nombre o usuario…"
                vacio={{ titulo: 'Sin usuarios', detalle: 'Registra al primero con «Registrar usuario».' }}
                columnas={[
                  { clave: 'nombre', titulo: 'Nombre', valor: (u) => u.nombre, celda: (u) => <strong className={estaDeBaja(u) ? 'text-body-secondary' : ''}>{u.nombre}</strong> },
                  { clave: 'usuario', titulo: 'Usuario', valor: (u) => u.usuario, celda: (u) => <span className="text-body-secondary">{u.usuario}</span> },
                  { clave: 'estado', titulo: 'Estado', valor: (u) => (estaDeBaja(u) ? 'b' : 'a'), celda: (u) => estaDeBaja(u)
                    ? <Chip tono="neutro">{u.fechaBaja ? `De baja desde ${fechaLima(u.fechaBaja)}` : 'De baja'}</Chip>
                    : <Chip tono="bien">Activo</Chip> },
                  { clave: 'alta', titulo: 'Alta', numerica: true, valor: (u) => u.creadoEn, celda: (u) => <span className="text-nowrap">{fechaLima(u.creadoEn)}</span> },
                ]}
                acciones={(u) => !estaDeBaja(u) && u.id !== usuarioSesionId
                  ? <AccionesFila de={u.nombre} acciones={[{ texto: 'Dar de baja', peligro: true, enClic: () => { setHecho(null); setBaja(u) } }]} />
                  : null}
              />
            )
          }}
        </Estado>
      </Tarjeta>

      {alta && (
        <AltaUsuario
          enCerrar={() => setAlta(false)}
          enCreado={async (u) => { setAlta(false); setHecho(`Se registró a ${u.nombre} (${u.usuario}). Entrégale la clave temporal en persona.`); await recargar() }}
        />
      )}
      {baja && (
        <Confirmar
          titulo="Dar de baja"
          accion="Dar de baja"
          texto={<>
            <strong>{baja.nombre}</strong> ya no podrá entrar a SIGRO y se le retiran sus asignaciones vigentes en todas
            las obras. Lo que registró se conserva. Queda «de baja» con la fecha de hoy.
          </>}
          enCerrar={() => setBaja(null)}
          enConfirmar={async () => {
            await administracion.darDeBaja(baja.id)
            setHecho(`${baja.nombre} quedó de baja.`)
            setBaja(null)
            await recargar()
          }}
        />
      )}
    </div>
  )
}

function AltaUsuario({ enCerrar, enCreado }: { enCerrar: () => void; enCreado: (u: Usuario) => Promise<void> }) {
  const [nombre, setNombre] = useState('')
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  // errores del servidor (p. ej. usuario repetido): van bajo su campo hasta que se vuelva a escribir en él
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  // validación en vivo con las mismas reglas del alta (la clave, con la regla del servidor)
  const v = useEnVivo<'nombre' | 'usuario' | 'clave'>(validarAltaUsuario({ nombre, usuario, clave }))
  const error = (k: 'nombre' | 'usuario' | 'clave') => errores[k] || v.errores[k] || undefined
  const escribir = (k: 'nombre' | 'usuario' | 'clave', poner: (x: string) => void) => (x: string) => {
    poner(x); v.cambio(k); setErrores((e) => ({ ...e, [k]: '' }))
  }

  const guardar = async () => {
    setGeneral(null)
    if (v.alGuardar()) return
    setOcupado(true)
    try {
      const u = await administracion.crearUsuario({ nombre, usuario, clave })
      setClave('') // la clave no se queda en memoria de la pantalla
      await enCreado(u)
    } catch (x) {
      const texto = mensajeParaPersona(x)
      const campo = x instanceof ErrorApi ? campoDelError(x.codigo) : null
      if (campo) setErrores({ [campo]: texto }) // p. ej. clave_debil: el mensaje de la API bajo el campo
      else setGeneral(texto)
      setOcupado(false)
    }
  }

  return (
    <Modal titulo="Registrar usuario" enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <Campo id="alta-nombre" etiqueta="Nombres y apellidos" valor={nombre} enCambio={escribir('nombre', setNombre)} autoFoco {...v.campo('nombre')} error={error('nombre')} />
        <Campo id="alta-usuario" etiqueta="Usuario para entrar" valor={usuario} marcador="Ej.: jperez" enCambio={escribir('usuario', setUsuario)} {...v.campo('usuario')} error={error('usuario')} />
        <div className="d-flex flex-column gap-2">
          <label htmlFor="alta-clave" className="small fw-semibold text-body-secondary">Clave temporal</label>
          <input className={`form-control${error('clave') ? ' is-invalid' : v.campo('clave').valido ? ' is-valid' : ''}`}
            id="alta-clave"
            type="password"
            autoComplete="new-password"
            value={clave}
            onChange={(e) => escribir('clave', setClave)(e.target.value)}
            onBlur={() => v.salir('clave')}
            aria-invalid={error('clave') ? true : undefined}
            aria-describedby="alta-clave-ayuda"

          />
          {error('clave')
            ? <span className="small text-danger" aria-live="polite">{error('clave')}</span>
            : <span id="alta-clave-ayuda" className="small text-body-secondary">Al menos 10 caracteres, con mayúsculas, minúsculas y un número. Entrégala en persona.</span>}
        </div>
        {general && <AvisoError texto={general} />}
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Registrar usuario'}</Boton>
        </div>
      </div>
    </Modal>
  )
}
