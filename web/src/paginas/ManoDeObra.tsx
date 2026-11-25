/* ─────────────────────────────────────────────────────────────────────────────
   HU-62 · Trabajadores de la obra (consulta de oficina)
   HU-64 · Como residente, quiero consultar las horas acumuladas por trabajador
           y por cuadrilla, para saber cuánta mano de obra lleva consumida la obra.

   Criterios y dónde se cumplen:
   - Horas normales y extra por separado → dos KPI y dos columnas.
   - Por trabajador, por cuadrilla y por periodo → selector de semana y vista.
   - Fecha de la última jornada considerada → bajo los KPI y en cada fila.
   - Un trabajador de baja no se borra (regla 10) → filtro «De baja» con su fecha.
   La web solo consulta: registrar, dar de baja y la asistencia van desde el
   celular del almacenero o del SSOMA. Las horas las calcula el servidor con la
   versión vigente de cada jornada (si se corrigió, vale la corrección).
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi } from '../api/cliente'
import { usaApiReal } from '../api/http'
import { manoDeObra, type HorasFila, type Trabajador } from '../api/manoDeObra'
import { Chip, Vacio } from '../ui/base'
import { Aviso, Chips, Encabezado, Kpi, TituloSeccion, Vidrio } from '../ui/piezas'
import { EsqueletoKpis } from '../ui/Esqueleto'
import { BotonExportar } from '../ui/BotonExportar'
import { activoEl, categoria, horas, resumirHoras, type FilaHoras } from './mano-de-obra-reglas'
import { TablaDatos } from '../ui/TablaDatos'

const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })   // AAAA-MM-DD
const dia = (iso: string | null) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '—')

type Vista = 'trabajador' | 'cuadrilla'
type Filtro = 'activos' | 'baja' | 'todos'

export function ManoDeObra() {
  const [trabajadores, setTrabajadores] = useState<Trabajador[] | null>(null)
  const [filas, setFilas] = useState<HorasFila[] | null>(null)
  const [error, setError] = useState('')
  const [periodo, setPeriodo] = useState<number | null>(null)
  const [vista, setVista] = useState<Vista>('trabajador')
  const [filtro, setFiltro] = useState<Filtro>('activos')
  const hoy = hoyLima()
  // en la obra = activo hoy o con ingreso programado; si no, de baja
  const enObra = (x: Trabajador) => activoEl(x, hoy) || x.fechaIngreso > hoy

  useEffect(() => {
    if (!usaApiReal) return
    Promise.all([manoDeObra.trabajadores(), manoDeObra.horas()])
      .then(([t, h]) => { setTrabajadores(t); setFilas(h) })
      .catch((e) => setError(e instanceof ErrorApi && e.codigo === 'sin_permiso'
        ? 'La lista de trabajadores la consultan el almacenero, el SSOMA, el residente y el administrador.'
        : e instanceof ErrorApi ? e.message : 'No se pudo cargar la mano de obra.'))
  }, [])

  const r = useMemo(() => resumirHoras(filas ?? [], periodo), [filas, periodo])
  const lista = useMemo(() => {
    return (trabajadores ?? []).filter((x) => filtro === 'todos' || (filtro === 'activos' ? enObra(x) : !enObra(x)))
  }, [trabajadores, filtro, hoy]) // eslint-disable-line react-hooks/exhaustive-deps
  const activos = (trabajadores ?? []).filter(enObra).length
  const filasHoras: FilaHoras[] = vista === 'trabajador' ? r.porTrabajador : r.porCuadrilla
  const textoPeriodo = periodo === null ? 'toda la obra' : `semana ${periodo}`

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado titulo="Mano de obra"
        sub="Quiénes trabajan en la obra y cuántas horas lleva consumidas cada trabajador y cada cuadrilla." />
      <Chips>
        <Chip tono="neutro">Lo registra el celular del almacenero o del SSOMA</Chip>
        <Chip tono="neutro">Horas de la versión vigente de cada jornada</Chip>
        <Chip tono="neutro">Nadie se borra: la baja es una fecha</Chip>
        <Chip tono="neutro">Son horas, no costo: la valorización por categoría (HU-65) espera la tarifa por hora del S10</Chip>
      </Chips>

      {!usaApiReal ? (
        <Vidrio><Vacio titulo="Necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: la mano de obra no se simula." /></Vidrio>
      ) : error ? (
        <Aviso tono="grave" rol="alert">{error}</Aviso>
      ) : !trabajadores || !filas ? (
        <EsqueletoKpis cuantas={4} />
      ) : (
        <>
          <div className="d-grid gap-3" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
            <Kpi rotulo="Trabajadores activos" valor={String(activos)} pie={`${trabajadores.length - activos} de baja`} />
            <Kpi rotulo="Horas normales" valor={horas(r.normales)} pie={textoPeriodo} />
            <Kpi rotulo="Horas extra" valor={horas(r.extra)} pie="se informan aparte" />
            <Kpi rotulo="Última jornada considerada" valor={dia(r.ultima)} pie="la más reciente que entró en la suma" />
          </div>

          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Horas acumuladas">
            <div className="d-flex flex-wrap justify-content-between align-items-end gap-2">
              <TituloSeccion>Horas acumuladas · {textoPeriodo}</TituloSeccion>
              <div className="d-flex flex-wrap gap-2 align-items-end">
                <label className="d-flex flex-column gap-1 small fw-semibold text-body-secondary">
                  Periodo
                  <select className="form-select" aria-label="Periodo" value={periodo ?? ''}
                    onChange={(e) => setPeriodo(e.target.value === '' ? null : Number(e.target.value))}>
                    <option value="">Toda la obra</option>
                    {r.periodos.map((p) => <option key={p} value={p}>Semana {p}</option>)}
                  </select>
                </label>
                <div className="btn-group" role="group" aria-label="Agrupar por">
                  {(['trabajador', 'cuadrilla'] as Vista[]).map((v) => (
                    <button key={v} type="button" aria-pressed={vista === v}
                      className={`btn ${vista === v ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setVista(v)}>
                      Por {v}
                    </button>
                  ))}
                </div>
                <BotonExportar
                  titulo="Horas acumuladas"
                  subtitulo={`Por ${vista} · ${textoPeriodo} · última jornada ${dia(r.ultima)}`}
                  filas={filasHoras}
                  columnas={[
                    { titulo: vista === 'trabajador' ? 'Trabajador' : 'Cuadrilla', valor: (f) => f.nombre, ancho: 30 },
                    { titulo: 'Detalle', valor: (f) => f.detalle, ancho: 30 },
                    { titulo: 'Días presente', valor: (f) => f.dias, formato: 'entero' },
                    { titulo: 'Horas normales', valor: (f) => f.normales, formato: 'decimal' },
                    { titulo: 'Horas extra', valor: (f) => f.extra, formato: 'decimal' },
                    { titulo: 'Última jornada', valor: (f) => dia(f.ultima) },
                  ]}
                />
              </div>
            </div>
            <TablaDatos
              key={vista}
              etiqueta="Horas acumuladas"
              filas={filasHoras}
              clave={(f) => f.clave}
              buscar={(f) => `${f.nombre} ${f.detalle}`}
              marcadorBusqueda={vista === 'trabajador' ? 'Buscar trabajador o cuadrilla…' : 'Buscar cuadrilla…'}
              vacio={{ titulo: 'Aún no hay jornadas enviadas', detalle: 'Las horas aparecen cuando la asistencia del celular llega al servidor.' }}
              columnas={[
                { clave: 'nombre', titulo: vista === 'trabajador' ? 'Trabajador' : 'Cuadrilla', valor: (f) => f.nombre, celda: (f) => <><strong>{f.nombre}</strong><div className="small text-body-secondary">{f.detalle}</div></> },
                { clave: 'dias', titulo: 'Días presente', numerica: true, valor: (f) => f.dias, celda: (f) => f.dias },
                { clave: 'normales', titulo: 'Horas normales', numerica: true, valor: (f) => f.normales, celda: (f) => <strong>{horas(f.normales)}</strong> },
                { clave: 'extra', titulo: 'Horas extra', numerica: true, valor: (f) => f.extra, celda: (f) => f.extra > 0 ? horas(f.extra) : '—' },
                { clave: 'ultima', titulo: 'Última jornada', numerica: true, valor: (f) => f.ultima, celda: (f) => <span className="text-nowrap">{dia(f.ultima)}</span> },
              ]}
            />
          </Vidrio>

          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Trabajadores">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
              <TituloSeccion>Trabajadores</TituloSeccion>
              <div className="btn-group" role="group" aria-label="Mostrar">
                {([['activos', 'Activos'], ['baja', 'De baja'], ['todos', 'Todos']] as [Filtro, string][]).map(([f, t]) => (
                  <button key={f} type="button" aria-pressed={filtro === f}
                    className={`btn btn-sm ${filtro === f ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setFiltro(f)}>
                    {t}
                  </button>
                ))}
              </div>
            </div>
            <TablaDatos
              etiqueta="Trabajadores"
              filas={lista}
              clave={(t) => t.id}
              buscar={(t) => `${t.nombre} ${t.documento} ${t.cuadrilla} ${categoria(t.categoria)}`}
              marcadorBusqueda="Buscar por nombre, DNI o cuadrilla…"
              vacio={{ titulo: 'Sin trabajadores para mostrar', detalle: 'Se registran desde el celular, en «Personal», con su categoría y cuadrilla.' }}
              columnas={[
                { clave: 'nombre', titulo: 'Trabajador', valor: (t) => t.nombre, celda: (t) => <><strong className={enObra(t) ? '' : 'text-body-secondary'}>{t.nombre}</strong><div className="small text-body-secondary">{t.tipoDocumento} {t.documento}</div></> },
                { clave: 'categoria', titulo: 'Categoría', valor: (t) => categoria(t.categoria), celda: (t) => categoria(t.categoria) },
                { clave: 'cuadrilla', titulo: 'Cuadrilla', valor: (t) => t.cuadrilla, celda: (t) => t.cuadrilla },
                { clave: 'ingreso', titulo: 'Ingreso', numerica: true, valor: (t) => t.fechaIngreso, celda: (t) => <span className="text-nowrap">{dia(t.fechaIngreso)}</span> },
                { clave: 'estado', titulo: 'Estado', valor: (t) => (enObra(t) ? 'a' : 'b') + (t.fechaSalida ?? ''), celda: (t) => enObra(t)
                  ? <Chip tono="bien">Activo</Chip> : <Chip tono="neutro">De baja desde {dia(t.fechaSalida)}</Chip> },
              ]}
            />
          </Vidrio>
        </>
      )}
    </div>
  )
}
