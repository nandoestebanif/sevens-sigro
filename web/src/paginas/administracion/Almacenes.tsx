/* Almacenes de la obra activa: lista y alta (central o de frente). El almacén elegido en cada movimiento
   sale de esta lista; el saldo de cada uno lo calcula el servidor. */
import { TablaDatos } from '../../ui/TablaDatos'
import { useState } from 'react'
import { useEnVivo } from '../../formularios/enVivo'
import { administracion, type TipoAlmacen } from '../../api/administracion'
import { Boton, Campo, Chip, Tarjeta } from '../../ui/base'
import { AvisoBien, AvisoError, Estado, Selector, useCarga } from './comun'
import { TIPOS_ALMACEN, mensajeParaPersona, textoTipoAlmacen } from './reglas'

export function Almacenes({ obraId, obraNombre }: { obraId: string; obraNombre: string }) {
  const { datos, error, recargar } = useCarga(() => administracion.almacenes(obraId), [obraId])
  const [nombre, setNombre] = useState('')
  const [tipo, setTipo] = useState<TipoAlmacen>('frente')
  const [aviso, setAviso] = useState<string | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const n = nombre.trim()
  const v = useEnVivo({ nombre: !n ? 'Escribe el nombre del almacén.' : n.length > 80 ? 'El nombre admite como máximo 80 caracteres.' : null })

  const crear = async () => {
    setAviso(null); setHecho(null)
    if (v.alGuardar()) return
    setOcupado(true)
    try {
      const a = await administracion.crearAlmacen(obraId, { nombre: n, tipo })
      setHecho(`Se registró el almacén «${a.nombre}» en ${obraNombre}.`)
      setNombre(''); v.reiniciar()
      await recargar()
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
          <div className="fw-bold">Nuevo almacén en {obraNombre}</div>
          <div className="gap-2 align-items-start" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            <Campo id="almacen-nombre" etiqueta="Nombre" valor={nombre} marcador="Ej.: Almacén torre B" enCambio={(x) => { setNombre(x); v.cambio('nombre') }} {...v.campo('nombre')} />
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
            <TablaDatos
              etiqueta={`Almacenes de ${obraNombre}`}
              filas={lista}
              clave={(a) => a.id}
              vacio={{ titulo: 'Sin almacenes', detalle: 'Crea el almacén central de la obra.' }}
              columnas={[
                { clave: 'nombre', titulo: 'Nombre', valor: (a) => a.nombre, celda: (a) => <strong>{a.nombre}</strong> },
                { clave: 'tipo', titulo: 'Tipo', valor: (a) => textoTipoAlmacen(a.tipo), celda: (a) => textoTipoAlmacen(a.tipo) },
                { clave: 'estado', titulo: 'Estado', valor: (a) => a.estado, celda: (a) => <Chip tono={a.estado === 'activo' ? 'bien' : 'neutro'}>{a.estado === 'activo' ? 'Activo' : 'Cerrado'}</Chip> },
              ]}
            />
          )}
        </Estado>
      </Tarjeta>
    </div>
  )
}
