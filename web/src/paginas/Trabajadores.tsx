/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · Trabajadores de la obra (HU-62, vista de oficina)

   La lista del personal: quiénes están en la obra, su categoría y cuadrilla, cuántas
   horas lleva cada uno y quiénes están de baja (nadie se borra, regla 10). El SSOMA y el
   almacenero los registran desde el celular; el administrador también desde aquí.
   ───────────────────────────────────────────────────────────────────────── */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ErrorApi } from '../api/cliente'
import { usaApiReal } from '../api/http'
import { manoDeObra, type HorasFila, type Trabajador } from '../api/manoDeObra'
import { puedo } from '../api/permisos'
import { Boton, Chip, Vacio } from '../ui/base'
import { BotonExportar } from '../ui/BotonExportar'
import { EsqueletoKpis } from '../ui/Esqueleto'
import { Aviso, Chips, Encabezado, Kpi, Segmentado, TituloSeccion, Vidrio } from '../ui/piezas'
import { AccionesFila, TablaDatos } from '../ui/TablaDatos'
import { CATEGORIAS, categoria, horas } from './mano-de-obra-reglas'
import { BajaTrabajador, DetalleTrabajador, FormTrabajador } from './TrabajadorFormularios'
import { enObra, filtrarTrabajadores, horasPorTrabajador, resumirPersonal, type EstadoTrabajador } from './trabajadores-reglas'

const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })
const dia = (iso: string | null) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '—')

export function Trabajadores() {
  const [trabajadores, setTrabajadores] = useState<Trabajador[] | null>(null)
  const [filasHoras, setFilasHoras] = useState<HorasFila[]>([])
  const [error, setError] = useState('')
  const [estado, setEstado] = useState<EstadoTrabajador>('activos')
  const [cat, setCat] = useState('')
  const [cuadrilla, setCuadrilla] = useState('')
  const [viendo, setViendo] = useState<Trabajador | null>(null)
  const [editando, setEditando] = useState<Trabajador | 'nuevo' | null>(null)
  const [bajando, setBajando] = useState<Trabajador | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)
  const puedeRegistrar = puedo('trabajador.registrar')
  const hoy = hoyLima()

  useEffect(() => {
    if (!usaApiReal) return
    manoDeObra.trabajadores().then(setTrabajadores).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar a los trabajadores.'))
    manoDeObra.horas().then(setFilasHoras).catch(() => setFilasHoras([]))       // sin horas, la lista igual se ve
  }, [])

  const reemplazar = useCallback((t: Trabajador) => setTrabajadores((xs) => {
    const l = xs ?? []
    return l.some((x) => x.id === t.id) ? l.map((x) => (x.id === t.id ? t : x)) : [t, ...l]
  }), [])
  const r = useMemo(() => resumirPersonal(trabajadores ?? [], hoy), [trabajadores, hoy])
  const hh = useMemo(() => horasPorTrabajador(filasHoras), [filasHoras])
  const lista = useMemo(() => filtrarTrabajadores(trabajadores ?? [], { estado, categoria: cat, cuadrilla }, hoy), [trabajadores, estado, cat, cuadrilla, hoy])

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado titulo="Trabajadores" sub="El personal de la obra: quién está, en qué categoría y cuadrilla, y cuántas horas lleva."
        acciones={puedeRegistrar ? <Boton enClic={() => { setHecho(null); setEditando('nuevo') }}>+ Registrar trabajador</Boton> : undefined} />
      <Chips>
        <Chip tono="neutro">Los registran el SSOMA o el almacenero en el celular, y el administrador también desde aquí</Chip>
        <Chip tono="neutro">Nadie se borra: la baja es una fecha</Chip>
        <Chip tono="neutro">Horas de la versión vigente de cada jornada</Chip>
      </Chips>

      {!usaApiReal ? (
        <Vidrio><Vacio titulo="Necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario." /></Vidrio>
      ) : error ? (
        <Aviso tono="grave" rol="alert">{error}</Aviso>
      ) : !trabajadores ? (
        <EsqueletoKpis cuantas={4} />
      ) : (
        <>
          <div className="d-grid gap-3" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))' }}>
            <Kpi rotulo="En la obra" valor={String(r.activos)} pie={`${r.deBaja} de baja`} />
            <Kpi rotulo="Operarios" valor={String(r.porCategoria.operario ?? 0)} pie="activos" />
            <Kpi rotulo="Oficiales" valor={String(r.porCategoria.oficial ?? 0)} pie="activos" />
            <Kpi rotulo="Peones" valor={String(r.porCategoria.peon ?? 0)} pie="activos" />
            <Kpi rotulo="Cuadrillas" valor={String(r.cuadrillasActivas)} pie="con personal activo" />
          </div>

          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Trabajadores">
            <div className="d-flex flex-wrap justify-content-between align-items-end gap-2">
              <TituloSeccion>Trabajadores · {lista.length}</TituloSeccion>
              <BotonExportar
                titulo="Trabajadores de la obra"
                subtitulo={`${estado === 'activos' ? 'En la obra' : estado === 'baja' ? 'De baja' : 'Todos'} al ${dia(hoy)}`}
                filas={lista}
                columnas={[
                  { titulo: 'Trabajador', valor: (t) => t.nombre, ancho: 34 },
                  { titulo: 'Documento', valor: (t) => `${t.tipoDocumento} ${t.documento}`, ancho: 16 },
                  { titulo: 'Categoría', valor: (t) => categoria(t.categoria) },
                  { titulo: 'Cuadrilla', valor: (t) => t.cuadrilla, ancho: 18 },
                  { titulo: 'Ingreso', valor: (t) => dia(t.fechaIngreso) },
                  { titulo: 'Salida', valor: (t) => dia(t.fechaSalida) },
                  { titulo: 'Horas normales', valor: (t) => hh.get(t.id)?.normales ?? 0, formato: 'decimal' },
                  { titulo: 'Horas extra', valor: (t) => hh.get(t.id)?.extra ?? 0, formato: 'decimal' },
                ]}
              />
            </div>
            <TablaDatos
              key={`${estado}-${cat}-${cuadrilla}`}
              etiqueta="Trabajadores"
              filas={lista}
              clave={(t) => t.id}
              buscar={(t) => `${t.nombre} ${t.documento} ${t.cuadrilla} ${categoria(t.categoria)}`}
              marcadorBusqueda="Buscar por nombre, DNI o cuadrilla…"
              vacio={{ titulo: 'Sin trabajadores para mostrar', detalle: 'Se registran desde el celular, en «Personal», con su categoría y cuadrilla.' }}
              filtros={(
                <div className="d-flex flex-wrap gap-2 align-items-center">
                  <Segmentado etiqueta="Estado" valor={estado} enCambio={setEstado}
                    opciones={[{ valor: 'activos', texto: 'En la obra' }, { valor: 'baja', texto: 'De baja' }, { valor: 'todos', texto: 'Todos' }]} />
                  <select className="form-select w-auto" aria-label="Categoría" value={cat} onChange={(e) => setCat(e.target.value)}>
                    <option value="">Todas las categorías</option>
                    {Object.entries(CATEGORIAS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                  </select>
                  {r.cuadrillas.length > 1 && (
                    <select className="form-select w-auto" aria-label="Cuadrilla" value={cuadrilla} onChange={(e) => setCuadrilla(e.target.value)}>
                      <option value="">Todas las cuadrillas</option>
                      {r.cuadrillas.map((c) => <option key={c} value={c}>{c}</option>)}
                    </select>
                  )}
                </div>
              )}
              acciones={(t) => (
                <AccionesFila de={t.nombre} acciones={[
                  { texto: 'Ver detalle', enClic: () => setViendo(t) },
                  puedeRegistrar && { texto: 'Corregir datos', enClic: () => { setHecho(null); setEditando(t) } },
                  puedeRegistrar && !t.fechaSalida && { texto: 'Dar de baja', peligro: true, enClic: () => { setHecho(null); setBajando(t) } },
                ]} />
              )}
              columnas={[
                { clave: 'nombre', titulo: 'Trabajador', valor: (t) => t.nombre,
                  celda: (t) => <><strong className={enObra(t, hoy) ? '' : 'text-body-secondary'}>{t.nombre}</strong><div className="small text-body-secondary">{t.tipoDocumento} {t.documento}</div></> },
                { clave: 'categoria', titulo: 'Categoría', valor: (t) => categoria(t.categoria), celda: (t) => categoria(t.categoria) },
                { clave: 'cuadrilla', titulo: 'Cuadrilla', valor: (t) => t.cuadrilla, celda: (t) => t.cuadrilla },
                { clave: 'horas', titulo: 'Horas', numerica: true, valor: (t) => (hh.get(t.id)?.normales ?? 0) + (hh.get(t.id)?.extra ?? 0),
                  celda: (t) => { const x = hh.get(t.id); return x ? <span className="text-nowrap">{horas(x.normales)}{x.extra > 0 && <span className="small text-body-secondary"> + {horas(x.extra)} extra</span>}</span> : '—' } },
                { clave: 'ingreso', titulo: 'Ingreso', numerica: true, valor: (t) => t.fechaIngreso, celda: (t) => <span className="text-nowrap">{dia(t.fechaIngreso)}</span>, soloEscritorio: true },
                { clave: 'estado', titulo: 'Estado', valor: (t) => (enObra(t, hoy) ? 'a' : 'b') + (t.fechaSalida ?? ''),
                  celda: (t) => (enObra(t, hoy) ? <Chip tono="bien">En la obra</Chip> : <Chip tono="neutro">De baja desde {dia(t.fechaSalida)}</Chip>) },
              ]}
            />
            {hecho && <span role="status" className="small text-success">{hecho}</span>}
          </Vidrio>
        </>
      )}
      {viendo && <DetalleTrabajador trabajador={viendo} horasDe={filasHoras} activo={enObra(viendo, hoy)} enCerrar={() => setViendo(null)} />}
      {editando && (
        <FormTrabajador trabajador={editando === 'nuevo' ? undefined : editando} enCerrar={() => setEditando(null)}
          enGuardado={(t) => { reemplazar(t); setHecho(editando === 'nuevo' ? `Se registró a ${t.nombre}.` : `Se corrigieron los datos de ${t.nombre}.`); setEditando(null) }} />
      )}
      {bajando && (
        <BajaTrabajador trabajador={bajando} enCerrar={() => setBajando(null)}
          enGuardado={(t) => { reemplazar(t); setHecho(`${t.nombre} quedó de baja desde el ${dia(t.fechaSalida)}.`); setBajando(null) }} />
      )}
    </div>
  )
}
