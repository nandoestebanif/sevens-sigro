/* HU-13 · Usuarios de la empresa. Alta con clave temporal (la regla de clave la aplica y la explica el
   servidor) y baja: nada se borra (regla 10), la persona queda «de baja» y pierde el acceso al instante. */
import { useState } from 'react'
import { ErrorApi } from '../../api/error'
import { administracion, type Usuario } from '../../api/administracion'
import { Boton, Campo, Chip, Modal, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Confirmar, Estado, Tabla, estiloCelda, estiloCeldaSuave, filaTabla, useCarga } from './comun'
import { campoDelError, estaDeBaja, fechaLima, mensajeParaPersona, ordenarUsuarios, validarAltaUsuario } from './reglas'

export function Usuarios({ usuarioSesionId }: { usuarioSesionId: number | null }) {
  const { datos, error, recargar } = useCarga(() => administracion.usuarios(), [])
  const [alta, setAlta] = useState(false)
  const [baja, setBaja] = useState<Usuario | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <p style={{ fontSize: 13, color: 'var(--tinta-3)', lineHeight: 1.5, flex: 1, minWidth: 260 }}>
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
              <Tabla etiqueta="Usuarios" titulos={['Nombre', 'Usuario', 'Estado', 'Alta', '']}>
                {filas.map((u, i) => (
                  <tr key={u.id} style={filaTabla(i === filas.length - 1)}>
                    <td style={{ ...estiloCelda, fontWeight: 600, color: estaDeBaja(u) ? 'var(--tinta-3)' : undefined }}>{u.nombre}</td>
                    <td className="n" style={estiloCeldaSuave}>{u.usuario}</td>
                    <td style={estiloCelda}>
                      {estaDeBaja(u)
                        ? <Chip tono="neutro">{u.fechaBaja ? `De baja desde ${fechaLima(u.fechaBaja)}` : 'De baja'}</Chip>
                        : <Chip tono="bien">Activo</Chip>}
                    </td>
                    <td className="n" style={estiloCeldaSuave}>{fechaLima(u.creadoEn)}</td>
                    <td style={{ ...estiloCelda, textAlign: 'right' }}>
                      {!estaDeBaja(u) && u.id !== usuarioSesionId && (
                        <Boton tipo="secundario" enClic={() => { setHecho(null); setBaja(u) }}>Dar de baja</Boton>
                      )}
                    </td>
                  </tr>
                ))}
              </Tabla>
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
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const guardar = async () => {
    const e = validarAltaUsuario({ nombre, usuario, clave })
    setErrores(e); setGeneral(null)
    if (Object.keys(e).length) return
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
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Campo id="alta-nombre" etiqueta="Nombres y apellidos" valor={nombre} enCambio={(v) => { setNombre(v); setErrores((e) => ({ ...e, nombre: '' })) }} autoFoco error={errores.nombre || undefined} />
        <Campo id="alta-usuario" etiqueta="Usuario para entrar" valor={usuario} marcador="Ej.: jperez" enCambio={(v) => { setUsuario(v); setErrores((e) => ({ ...e, usuario: '' })) }} error={errores.usuario || undefined} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label htmlFor="alta-clave" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>Clave temporal</label>
          <input
            id="alta-clave"
            type="password"
            autoComplete="new-password"
            value={clave}
            onChange={(e) => { setClave(e.target.value); setErrores((x) => ({ ...x, clave: '' })) }}
            aria-invalid={errores.clave ? true : undefined}
            aria-describedby="alta-clave-ayuda"
            style={{
              height: 42, border: `1px solid ${errores.clave ? 'var(--grave)' : 'var(--borde)'}`, borderRadius: 'var(--radio-chico)',
              padding: '0 13px', fontSize: 14, fontFamily: 'inherit', color: 'var(--tinta)', background: 'var(--tarjeta)',
            }}
          />
          {errores.clave
            ? <span role="alert" style={{ fontSize: 12, color: 'var(--grave)' }}>{errores.clave}</span>
            : <span id="alta-clave-ayuda" style={{ fontSize: 12, color: 'var(--tinta-3)' }}>El servidor revisa que sea segura y te dice si no lo es. Entrégala en persona.</span>}
        </div>
        {general && <AvisoError texto={general} />}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Registrar usuario'}</Boton>
        </div>
      </div>
    </Modal>
  )
}
