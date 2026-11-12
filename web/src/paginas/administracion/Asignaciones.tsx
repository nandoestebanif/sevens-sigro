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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Tarjeta>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ fontSize: 14.5, fontWeight: 700 }}>Asignar a {obraNombre}</div>
          {usuarios.error && <AvisoError texto={usuarios.error} />}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, alignItems: 'start' }}>
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
            <span style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
              Todos los usuarios activos ya están asignados a esta obra. Registra uno nuevo en «Usuarios».
            </span>
          )}
          <div><Boton enClic={asignar} deshabilitado={ocupado || usuarios.datos === null}>{ocupado ? 'Guardando…' : 'Asignar'}</Boton></div>
        </div>
      </Tarjeta>

      {hecho && <AvisoBien texto={hecho} />}
      {aviso && <AvisoError texto={aviso} />}

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--tinta-2)' }}>
        <input type="checkbox" checked={verRetiradas} onChange={(e) => setVerRetiradas(e.target.checked)} />
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
