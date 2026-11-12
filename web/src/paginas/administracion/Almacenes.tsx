/* Almacenes de la obra activa: lista y alta (central o de frente). El almacén elegido en cada movimiento
   sale de esta lista; el saldo de cada uno lo calcula el servidor. */
import { useState } from 'react'
import { administracion, type TipoAlmacen } from '../../api/administracion'
import { Boton, Campo, Chip, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Estado, Selector, Tabla, estiloCelda, estiloCeldaSuave, filaTabla, useCarga } from './comun'
import { TIPOS_ALMACEN, mensajeParaPersona, textoTipoAlmacen } from './reglas'

export function Almacenes({ obraId, obraNombre }: { obraId: string; obraNombre: string }) {
  const { datos, error, recargar } = useCarga(() => administracion.almacenes(obraId), [obraId])
  const [nombre, setNombre] = useState('')
  const [tipo, setTipo] = useState<TipoAlmacen>('frente')
  const [errorNombre, setErrorNombre] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const crear = async () => {
    setAviso(null); setHecho(null)
    const n = nombre.trim()
    if (!n) { setErrorNombre('Escribe el nombre del almacén.'); return }
    if (n.length > 80) { setErrorNombre('El nombre admite como máximo 80 caracteres.'); return }
    setOcupado(true)
    try {
      const a = await administracion.crearAlmacen(obraId, { nombre: n, tipo })
      setHecho(`Se registró el almacén «${a.nombre}» en ${obraNombre}.`)
      setNombre('')
      await recargar()
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
          <div style={{ fontSize: 14.5, fontWeight: 700 }}>Nuevo almacén en {obraNombre}</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, alignItems: 'start' }}>
            <Campo id="almacen-nombre" etiqueta="Nombre" valor={nombre} marcador="Ej.: Almacén torre B" enCambio={(v) => { setNombre(v); setErrorNombre(null) }} error={errorNombre ?? undefined} />
            <Selector id="almacen-tipo" etiqueta="Tipo" valor={tipo} opciones={TIPOS_ALMACEN} enCambio={setTipo} />
          </div>
          <div><Boton enClic={crear} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Registrar almacén'}</Boton></div>
        </div>
      </Tarjeta>
      {hecho && <AvisoBien texto={hecho} />}
      {aviso && <AvisoError texto={aviso} />}
      <Tarjeta relleno={false}>
        <Estado
          datos={datos} error={error} recargar={recargar}
          vacio={{ titulo: 'La obra no tiene almacenes', detalle: 'Registra al menos uno: sin almacén no se pueden registrar ingresos ni salidas.' }}
        >
          {(lista) => (
            <Tabla etiqueta={`Almacenes de ${obraNombre}`} titulos={['Nombre', 'Tipo', 'Estado']}>
              {lista.map((a, i) => (
                <tr key={a.id} style={filaTabla(i === lista.length - 1)}>
                  <td style={{ ...estiloCelda, fontWeight: 600 }}>{a.nombre}</td>
                  <td style={estiloCeldaSuave}>{textoTipoAlmacen(a.tipo)}</td>
                  <td style={estiloCelda}><Chip tono={a.estado === 'activo' ? 'bien' : 'neutro'}>{a.estado === 'activo' ? 'Activo' : 'Cerrado'}</Chip></td>
                </tr>
              ))}
            </Tabla>
          )}
        </Estado>
      </Tarjeta>
    </div>
  )
}
