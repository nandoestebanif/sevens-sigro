/* HU-14 · Quién trabaja en la obra activa y con qué rol (regla 9: el permiso vive junto a la obra).
   Asignar y retirar; un retiro no borra: queda con su fecha. La obra no se queda sin administrador (lo cuida la API). */
import { useState } from 'react'
import { administracion, type Asignacion, type Rol, type Usuario } from '../../api/administracion'
import { Boton, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Confirmar, Estado, Selector, Tabla, estiloCelda, estiloCeldaSuave, filaTabla, useCarga } from './comun'
import { ROLES, asignables, fechaLima, mensajeParaPersona, textoRol } from './reglas'

export function Asignaciones({ obraId, obraNombre }: { obraId: string; obraNombre: string }) {
  const [verRetiradas, setVerRetiradas] = useState(false)
  const lista = useCarga(() => administracion.asignaciones(obraId, !verRetiradas), [obraId, verRetiradas])
  const usuarios = useCarga<Usuario[]>(() => administracion.usuarios(), [])
  const [usuarioId, setUsuarioId] = useState<number | ''>('')
  const [rol, setRol] = useState<Rol | ''>('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<string | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [retirando, setRetirando] = useState<Asignacion | null>(null)

  const vigentes = (lista.datos ?? []).filter((a) => a.fechaRetiro == null)
  const candidatos = usuarios.datos ? asignables(usuarios.datos, vigentes) : []

  const asignar = async () => {
    const e: Record<string, string> = {}
    if (usuarioId === '') e.usuario = 'Elige a la persona.'
    if (rol === '') e.rol = 'Elige el rol en esta obra.'
    setErrores(e); setAviso(null); setHecho(null)
    if (Object.keys(e).length) return
    setOcupado(true)
    try {
      const a = await administracion.asignar(obraId, usuarioId as number, rol as Rol)
      setHecho(`${a.usuarioNombre} quedó asignado a ${obraNombre} como ${textoRol(a.rol).toLowerCase()}.`)
      setUsuarioId(''); setRol('')
      await lista.recargar()
    } catch (x) {
      setAviso(mensajeParaPersona(x))
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="d-flex flex-column gap-3">
      <Tarjeta>
        <div className="d-flex flex-column gap-3">
          <div className="fw-bold">Asignar a {obraNombre}</div>
          {usuarios.error && <AvisoError texto={usuarios.error} />}
          <div className="gap-2 align-items-start" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            <Selector
              id="asignar-usuario" etiqueta="Persona" valor={usuarioId}
              opciones={candidatos.map((u) => ({ valor: u.id, texto: `${u.nombre} (${u.usuario})` }))}
              enCambio={(v) => { setUsuarioId(v); setErrores((e) => ({ ...e, usuario: '' })) }}
              error={errores.usuario || undefined}
            />
            <Selector
              id="asignar-rol" etiqueta="Rol en esta obra" valor={rol} opciones={ROLES}
              enCambio={(v) => { setRol(v); setErrores((e) => ({ ...e, rol: '' })) }}
              error={errores.rol || undefined}
            />
          </div>
          {usuarios.datos && candidatos.length === 0 && (
            <span className="small text-body-secondary">
              Todos los usuarios activos ya están asignados a esta obra. Registra uno nuevo en «Usuarios».
            </span>
          )}
          <div><Boton enClic={asignar} deshabilitado={ocupado || usuarios.datos === null}>{ocupado ? 'Guardando…' : 'Asignar'}</Boton></div>
        </div>
      </Tarjeta>

      {hecho && <AvisoBien texto={hecho} />}
      {aviso && <AvisoError texto={aviso} />}

      <label className="d-flex align-items-center gap-2 small text-body-secondary">
        <input className="form-check-input mt-0" type="checkbox" checked={verRetiradas} onChange={(e) => setVerRetiradas(e.target.checked)} />
        Mostrar también las asignaciones retiradas
      </label>

      <Tarjeta relleno={false}>
        <Estado
          datos={lista.datos} error={lista.error} recargar={lista.recargar}
          vacio={{ titulo: 'Nadie asignado todavía', detalle: `Asigna a las personas que trabajan en ${obraNombre}.` }}
        >
          {(xs) => (
            <Tabla etiqueta={`Asignaciones de ${obraNombre}`} titulos={['Persona', 'Usuario', 'Rol', 'Desde', 'Hasta', '']}>
              {xs.map((a, i) => (
                <tr key={a.id} style={filaTabla(i === xs.length - 1)}>
                  <td style={{ ...estiloCelda, fontWeight: 600, color: a.fechaRetiro ? 'var(--tinta-3)' : undefined }}>{a.usuarioNombre}</td>
                  <td className="n" style={estiloCeldaSuave}>{a.usuario}</td>
                  <td style={estiloCelda}>{textoRol(a.rol)}</td>
                  <td className="n" style={estiloCeldaSuave}>{fechaLima(a.fechaAsignacion)}</td>
                  <td className="n" style={estiloCeldaSuave}>{a.fechaRetiro ? fechaLima(a.fechaRetiro) : 'vigente'}</td>
                  <td style={{ ...estiloCelda, textAlign: 'right' }}>
                    {!a.fechaRetiro && <Boton tipo="secundario" enClic={() => { setHecho(null); setAviso(null); setRetirando(a) }}>Retirar</Boton>}
                  </td>
                </tr>
              ))}
            </Tabla>
          )}
        </Estado>
      </Tarjeta>

      {retirando && (
        <Confirmar
          titulo="Retirar de la obra"
          accion="Retirar"
          texto={<>
            <strong>{retirando.usuarioNombre}</strong> deja de ser {textoRol(retirando.rol).toLowerCase()} en {obraNombre} y
            pierde el acceso a esta obra. La asignación no se borra: queda con la fecha de retiro.
          </>}
          enCerrar={() => setRetirando(null)}
          enConfirmar={async () => {
            await administracion.retirar(obraId, retirando.id)
            setHecho(`${retirando.usuarioNombre} fue retirado de ${obraNombre}.`)
            setRetirando(null)
            await lista.recargar()
          }}
        />
      )}
    </div>
  )
}
