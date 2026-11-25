/* HU-13 · Usuarios de la empresa. Alta con clave temporal (la regla de clave la aplica y la explica el
   servidor) y baja: nada se borra (regla 10), la persona queda «de baja» y pierde el acceso al instante.
   Desde la migración 0021: DNI (con el nombre de RENIEC), correo y celular, y clave temporal para quien olvidó
   la suya (el administrador se la entrega en persona; la persona la cambia con «Cambiar mi contraseña»). */
import { AccionesFila, TablaDatos } from '../../ui/TablaDatos'
import { useState } from 'react'
import { useEnVivo } from '../../formularios/enVivo'
import { claveAceptable } from '../../formularios/reglas'
import { ErrorApi } from '../../api/error'
import { administracion, type Usuario } from '../../api/administracion'
import { Boton, Campo, Chip, Modal, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Confirmar, Estado, useCarga } from './comun'
import {
  campoDelError, estaDeBaja, fechaLima, mensajeParaPersona, ordenarUsuarios, validarAltaUsuario, validarContacto,
} from './reglas'

type Errores = Record<string, string>
type CampoAlta = 'nombre' | 'usuario' | 'clave' | 'dni' | 'correo' | 'celular'
type CampoContacto = 'dni' | 'correo' | 'celular'

export function Usuarios({ usuarioSesionId }: { usuarioSesionId: number | null }) {
  const { datos, error, recargar } = useCarga(() => administracion.usuarios(), [])
  const [alta, setAlta] = useState(false)
  const [baja, setBaja] = useState<Usuario | null>(null)
  const [contacto, setContacto] = useState<Usuario | null>(null)
  const [clave, setClave] = useState<Usuario | null>(null)
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
          {(lista) => (
            <TablaDatos
              etiqueta="Usuarios"
              filas={ordenarUsuarios(lista)}
              clave={(u) => u.id}
              buscar={(u) => `${u.nombre} ${u.usuario} ${u.dni ?? ''} ${u.correo ?? ''} ${u.celular ?? ''}`}
              marcadorBusqueda="Buscar por nombre, usuario, DNI o correo…"
              vacio={{ titulo: 'Sin usuarios', detalle: 'Registra al primero con «Registrar usuario».' }}
              columnas={[
                { clave: 'nombre', titulo: 'Nombre', valor: (u) => u.nombre, celda: (u) => (
                  <div>
                    <strong className={estaDeBaja(u) ? 'text-body-secondary' : ''}>{u.nombre}</strong>
                    {u.dni && <div className="small text-body-secondary">DNI {u.dni}</div>}
                  </div>
                ) },
                { clave: 'usuario', titulo: 'Usuario', valor: (u) => u.usuario, celda: (u) => <span className="text-body-secondary">{u.usuario}</span> },
                { clave: 'contacto', titulo: 'Contacto', soloEscritorio: true, valor: (u) => u.correo ?? u.celular ?? '', celda: (u) => (
                  u.correo || u.celular
                    ? <div className="small">{u.correo && <div>{u.correo}</div>}{u.celular && <div className="text-body-secondary">{u.celular}</div>}</div>
                    : <span className="small text-body-tertiary">—</span>
                ) },
                { clave: 'estado', titulo: 'Estado', valor: (u) => (estaDeBaja(u) ? 'b' : 'a'), celda: (u) => estaDeBaja(u)
                  ? <Chip tono="neutro">{u.fechaBaja ? `De baja desde ${fechaLima(u.fechaBaja)}` : 'De baja'}</Chip>
                  : <Chip tono="bien">Activo</Chip> },
                { clave: 'alta', titulo: 'Alta', numerica: true, valor: (u) => u.creadoEn, celda: (u) => <span className="text-nowrap">{fechaLima(u.creadoEn)}</span> },
              ]}
              acciones={(u) => estaDeBaja(u) ? null : (
                <AccionesFila de={u.nombre} acciones={[
                  { texto: 'Editar contacto', enClic: () => { setHecho(null); setContacto(u) } },
                  ...(u.id !== usuarioSesionId ? [
                    { texto: 'Nueva clave temporal', enClic: () => { setHecho(null); setClave(u) } },
                    { texto: 'Dar de baja', peligro: true, enClic: () => { setHecho(null); setBaja(u) } },
                  ] : []),
                ]} />
              )}
            />
          )}
        </Estado>
      </Tarjeta>

      {alta && (
        <AltaUsuario
          enCerrar={() => setAlta(false)}
          enCreado={async (u) => { setAlta(false); setHecho(`Se registró a ${u.nombre} (${u.usuario}). Entrégale la clave temporal en persona.`); await recargar() }}
        />
      )}
      {contacto && (
        <EditarContacto
          usuario={contacto}
          enCerrar={() => setContacto(null)}
          enGuardado={async (u) => { setContacto(null); setHecho(`Se actualizó el contacto de ${u.nombre}.`); await recargar() }}
        />
      )}
      {clave && (
        <ClaveTemporal
          usuario={clave}
          enCerrar={() => setClave(null)}
          enGuardado={(u) => { setClave(null); setHecho(`${u.nombre} ya puede entrar con la clave temporal. Entrégasela en persona y pídele que la cambie.`) }}
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

/** Error del servidor: bajo su campo si se sabe cuál es; si no, arriba de los botones. */
function repartirError(x: unknown, setErrores: (e: Errores) => void, setGeneral: (t: string) => void) {
  const texto = mensajeParaPersona(x)
  const campo = x instanceof ErrorApi ? campoDelError(x.codigo) : null
  if (campo) setErrores({ [campo]: texto })
  else setGeneral(texto)
}

/** DNI con botón «Buscar en RENIEC»: trae el nombre tal como está en el padrón (si no responde, se escribe a mano). */
function CampoDni({ id, dni, setDni, error, valido, enSalir, enNombre }: {
  id: string; dni: string; setDni: (v: string) => void; error?: string; valido?: boolean; enSalir?: () => void
  enNombre?: (nombre: string) => void
}) {
  const [buscando, setBuscando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const listo = /^\d{8}$/.test(dni.replace(/\s/g, ''))
  const buscar = async () => {
    setBuscando(true); setAviso(null)
    try {
      const r = await administracion.consultarDni(dni)
      enNombre?.(r.nombreCompleto)
      setAviso(`RENIEC: ${r.nombreCompleto}`)
    } catch (x) {
      setAviso(x instanceof ErrorApi && x.codigo === 'reniec_sin_respuesta'
        ? 'RENIEC no respondió: escribe el nombre a mano.' : mensajeParaPersona(x))
    } finally { setBuscando(false) }
  }
  return (
    <div className="d-flex flex-column gap-1">
      <div className="d-flex gap-2 align-items-end">
        <div className="flex-grow-1">
          <Campo id={id} etiqueta="DNI (opcional)" valor={dni} marcador="8 dígitos" modoTeclado="numeric"
            enCambio={(v) => { setDni(v); setAviso(null) }} error={error} valido={valido} enSalir={enSalir} />
        </div>
        {enNombre && (
          <Boton tipo="secundario" enClic={buscar} deshabilitado={!listo || buscando}>
            {buscando ? 'Buscando…' : 'Buscar en RENIEC'}
          </Boton>
        )}
      </div>
      {aviso && <span role="status" className="small text-body-secondary">{aviso}</span>}
    </div>
  )
}

function AltaUsuario({ enCerrar, enCreado }: { enCerrar: () => void; enCreado: (u: Usuario) => Promise<void> }) {
  const [nombre, setNombre] = useState('')
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [dni, setDni] = useState('')
  const [correo, setCorreo] = useState('')
  const [celular, setCelular] = useState('')
  // errores del servidor (p. ej. usuario o DNI repetido): van bajo su campo hasta que se vuelva a escribir en él
  const [errores, setErrores] = useState<Errores>({})
  const [general, setGeneral] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  // validación en vivo con las mismas reglas del alta (la clave, con la regla del servidor)
  const v = useEnVivo<CampoAlta>(validarAltaUsuario({ nombre, usuario, clave, dni, correo, celular }))
  const error = (k: CampoAlta) => errores[k] || v.errores[k] || undefined
  const escribir = (k: CampoAlta, poner: (x: string) => void) => (x: string) => {
    poner(x); v.cambio(k); setErrores((e) => ({ ...e, [k]: '' }))
  }
  const props = (k: CampoAlta) => ({ ...v.campo(k), error: error(k) })

  const guardar = async () => {
    setGeneral(null)
    if (v.alGuardar()) return
    setOcupado(true)
    try {
      const u = await administracion.crearUsuario({ nombre, usuario, clave, dni, correo, celular })
      setClave('') // la clave no se queda en memoria de la pantalla
      await enCreado(u)
    } catch (x) {
      repartirError(x, setErrores, setGeneral)
      setOcupado(false)
    }
  }

  return (
    <Modal titulo="Registrar usuario" enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <CampoDni id="alta-dni" dni={dni} setDni={escribir('dni', setDni)} {...props('dni')}
          enNombre={(n) => { setNombre(n); v.salir('nombre'); setErrores((e) => ({ ...e, nombre: '' })) }} />
        <Campo id="alta-nombre" etiqueta="Nombres y apellidos" valor={nombre} enCambio={escribir('nombre', setNombre)} {...props('nombre')} />
        <Campo id="alta-usuario" etiqueta="Usuario para entrar" valor={usuario} marcador="Ej.: jperez" enCambio={escribir('usuario', setUsuario)} {...props('usuario')} />
        <div className="d-flex gap-3 flex-wrap">
          <div className="flex-grow-1" style={{ minWidth: 200 }}>
            <Campo id="alta-correo" etiqueta="Correo (opcional)" tipo="email" valor={correo} marcador="nombre@empresa.com" enCambio={escribir('correo', setCorreo)} {...props('correo')} />
          </div>
          <div style={{ minWidth: 160 }}>
            <Campo id="alta-celular" etiqueta="Celular (opcional)" modoTeclado="numeric" valor={celular} marcador="9xx xxx xxx" enCambio={escribir('celular', setCelular)} {...props('celular')} />
          </div>
        </div>
        <Campo id="alta-clave" etiqueta="Clave temporal" tipo="password" valor={clave} enCambio={escribir('clave', setClave)} {...props('clave')}
          ayuda="Al menos 10 caracteres, con mayúsculas, minúsculas y un número. Entrégala en persona." />
        {general && <AvisoError texto={general} />}
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Registrar usuario'}</Boton>
        </div>
      </div>
    </Modal>
  )
}

function EditarContacto({ usuario, enCerrar, enGuardado }: {
  usuario: Usuario; enCerrar: () => void; enGuardado: (u: Usuario) => Promise<void>
}) {
  const [dni, setDni] = useState(usuario.dni ?? '')
  const [correo, setCorreo] = useState(usuario.correo ?? '')
  const [celular, setCelular] = useState(usuario.celular ?? '')
  const [errores, setErrores] = useState<Errores>({})
  const [general, setGeneral] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const v = useEnVivo<CampoContacto>(validarContacto({ dni, correo, celular }))
  const escribir = (k: CampoContacto, poner: (x: string) => void) => (x: string) => {
    poner(x); v.cambio(k); setErrores((e) => ({ ...e, [k]: '' }))
  }
  const props = (k: CampoContacto) => ({ ...v.campo(k), error: errores[k] || v.errores[k] || undefined })

  const guardar = async () => {
    setGeneral(null)
    if (v.alGuardar()) return
    setOcupado(true)
    try {
      await enGuardado(await administracion.actualizarContacto(usuario.id, { dni, correo, celular }))
    } catch (x) {
      repartirError(x, setErrores, setGeneral)
      setOcupado(false)
    }
  }

  return (
    <Modal titulo={`Contacto de ${usuario.nombre}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <CampoDni id="contacto-dni" dni={dni} setDni={escribir('dni', setDni)} {...props('dni')} />
        <Campo id="contacto-correo" etiqueta="Correo (opcional)" tipo="email" valor={correo} marcador="nombre@empresa.com" enCambio={escribir('correo', setCorreo)} {...props('correo')} />
        <Campo id="contacto-celular" etiqueta="Celular (opcional)" modoTeclado="numeric" valor={celular} marcador="9xx xxx xxx" enCambio={escribir('celular', setCelular)} {...props('celular')}
          ayuda="Deja un campo vacío para quitarlo." />
        {general && <AvisoError texto={general} />}
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Guardar'}</Boton>
        </div>
      </div>
    </Modal>
  )
}

function ClaveTemporal({ usuario, enCerrar, enGuardado }: { usuario: Usuario; enCerrar: () => void; enGuardado: (u: Usuario) => void }) {
  const [clave, setClave] = useState('')
  const [delServidor, setDelServidor] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const v = useEnVivo<'clave'>({ clave: clave ? claveAceptable(clave) : 'Escribe una clave temporal.' })

  const guardar = async () => {
    setDelServidor(null)
    if (v.alGuardar()) return
    setOcupado(true)
    try {
      const u = await administracion.claveTemporal(usuario.id, clave)
      setClave('')
      enGuardado(u)
    } catch (x) {
      setDelServidor(mensajeParaPersona(x))
      setOcupado(false)
    }
  }

  return (
    <Modal titulo="Nueva clave temporal" enCerrar={enCerrar}>
      <form className="d-flex flex-column gap-3" onSubmit={(e) => { e.preventDefault(); void guardar() }}>
        <span className="small">
          Para <strong>{usuario.nombre}</strong> ({usuario.usuario}). Su clave anterior deja de servir y se le quita el
          bloqueo por intentos fallidos.
        </span>
        <Campo id="clave-temporal" etiqueta="Clave temporal" tipo="password" valor={clave}
          enCambio={(x) => { setClave(x); v.cambio('clave'); setDelServidor(null) }}
          {...v.campo('clave')} error={delServidor || v.errores.clave || undefined}
          ayuda="Al menos 10 caracteres, con mayúsculas, minúsculas y un número. Entrégala en persona; al entrar, que la cambie en «Cambiar mi contraseña»." />
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Poner clave temporal'}</Boton>
        </div>
      </form>
    </Modal>
  )
}
