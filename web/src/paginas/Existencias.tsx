/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · Insumos en almacén y Herramientas y equipos (HU-43, vista de oficina)

   Qué hay en cada almacén de la obra, cuánto y si toca reponer. Todo lo calcula el
   servidor a partir del libro de movimientos (regla 2): saldo por almacén, saldo de
   la obra, punto de reposición y stock de seguridad ya convertidos a unidad de compra.
   La misma pantalla, con `soloEquipos`, lista las herramientas y equipos (tipo «equipo»
   del catálogo), para controlar lo que se presta a las cuadrillas.
   ───────────────────────────────────────────────────────────────────────── */
import { useMemo, useState } from 'react'
import { ErrorApi } from '../api/cliente'
import { usaApiReal } from '../api/http'
import { existencias } from '../api/existencias'
import { puedo } from '../api/permisos'
import { Chip, Vacio } from '../ui/base'
import { BotonExportar } from '../ui/BotonExportar'
import { EsqueletoKpis } from '../ui/Esqueleto'
import { Aviso, Chips, Encabezado, Kpi, Segmentado, TituloSeccion, Vidrio } from '../ui/piezas'
import { TablaDatos } from '../ui/TablaDatos'
import { TEXTO_ESTADO, dondeEsta, filtrar, ordenarPorUrgencia, resumir, type EstadoExistencia, type Existencia, type FiltroEstado } from './existencias-reglas'
import { EnVivo, useActualizacion } from '../ui/Actualizacion'

const cifra = (n: number) => n.toLocaleString('es-PE', { maximumFractionDigits: 2 })
const soles = (n: number) => n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const fechaHora = (iso: string | null) => (iso
  ? new Date(iso).toLocaleString('es-PE', { timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  : '—')
const TONO: Record<EstadoExistencia, 'grave' | 'aviso' | 'bien'> = { sin_stock: 'grave', bajo_seguridad: 'grave', bajo_punto: 'aviso', normal: 'bien' }

export function Existencias({ soloEquipos = false }: { soloEquipos?: boolean }) {
  const [datos, setDatos] = useState<{ filas: Existencia[]; almacenes: string[]; corte: string | null; advertencias: string[] } | null>(null)
  const [error, setError] = useState('')
  const [estado, setEstado] = useState<FiltroEstado>('todos')
  const [almacen, setAlmacen] = useState('')
  const veCosto = puedo('costo.consultar')         // el valor lo ven residente, administrador y gerente
  const titulo = soloEquipos ? 'Herramientas y equipos' : 'Insumos en almacén'

  const vivo = useActualizacion(() => existencias().then((d) => { setDatos(d); setError('') })
    .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo consultar el almacén.')), { activo: usaApiReal })

  const base = useMemo(() => (datos ? datos.filas.filter((x) => !soloEquipos || x.tipo === 'equipo') : []), [datos, soloEquipos])
  const filas = useMemo(() => ordenarPorUrgencia(filtrar(base, { estado, almacen, soloEquipos })), [base, estado, almacen, soloEquipos])
  const r = resumir(base)

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado titulo={titulo} acciones={usaApiReal ? <EnVivo {...vivo} /> : undefined}
        sub={soloEquipos
          ? 'Las herramientas y equipos que hay en los almacenes de la obra: cuántos, dónde y cuáles faltan.'
          : 'Lo que hay en cada almacén de la obra, cuánto y qué toca reponer.'} />
      <Chips>
        <Chip tono="neutro">El saldo lo calcula el servidor a partir del libro de movimientos</Chip>
        <Chip tono="neutro">Punto de reposición y stock de seguridad en unidad de compra</Chip>
        {soloEquipos && <Chip tono="neutro">Herramientas y equipos = insumos de tipo «equipo» en el catálogo</Chip>}
        {datos?.advertencias.map((a) => <Chip key={a} tono="aviso">{a}</Chip>)}
      </Chips>

      {!usaApiReal ? (
        <Vidrio><Vacio titulo="Necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: el almacén no se simula." /></Vidrio>
      ) : error ? (
        <Aviso tono="grave" rol="alert">{error}</Aviso>
      ) : !datos ? (
        <EsqueletoKpis cuantas={4} />
      ) : (
        <>
          <div className="d-grid gap-3" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
            <Kpi rotulo={soloEquipos ? 'Herramientas y equipos con stock' : 'Insumos con stock'} valor={String(r.conStock)} pie={`de ${base.length} con movimientos`} />
            <Kpi rotulo="Por reponer" valor={String(r.porReponer)} pie="bajo el punto de reposición o el de seguridad" />
            <Kpi rotulo="Sin stock" valor={String(r.sinStock)} pie="se movieron y hoy no queda nada" />
            {veCosto
              ? <Kpi rotulo="Valor en almacén" prefijo="S/" valor={soles(r.valor)} pie="al costo promedio, sin IGV" />
              : <Kpi rotulo="Último movimiento" valor={fechaHora(r.ultimo)} pie="el más reciente considerado" />}
          </div>

          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta={titulo}>
            <div className="d-flex flex-wrap justify-content-between align-items-end gap-2">
              <TituloSeccion>{titulo} · {filas.length} de {base.length}</TituloSeccion>
              <BotonExportar
                titulo={titulo}
                subtitulo={`Corte: ${fechaHora(datos.corte)}${almacen ? ` · almacén ${almacen}` : ''}`}
                filas={filas}
                columnas={[
                  { titulo: 'Código', valor: (x) => x.codigo, ancho: 14 },
                  { titulo: 'Insumo', valor: (x) => x.descripcion, ancho: 40 },
                  { titulo: 'Unidad', valor: (x) => x.unidad },
                  { titulo: 'Cantidad', valor: (x) => x.cantidad, formato: 'decimal' },
                  { titulo: 'Dónde está', valor: (x) => dondeEsta(x, cifra), ancho: 30 },
                  { titulo: 'Punto de reposición', valor: (x) => x.puntoReposicion, formato: 'decimal' },
                  { titulo: 'Estado', valor: (x) => TEXTO_ESTADO[x.estado], ancho: 22 },
                  ...(veCosto ? [{ titulo: 'Valor S/', valor: (x: Existencia) => x.valor, formato: 'soles' as const }] : []),
                ]}
              />
            </div>
            <TablaDatos
              key={`${estado}-${almacen}`}
              etiqueta={titulo}
              filas={filas}
              clave={(x) => x.insumoId}
              buscar={(x) => `${x.codigo} ${x.descripcion}`}
              marcadorBusqueda="Buscar por código o nombre…"
              vacio={{ titulo: soloEquipos ? 'No hay herramientas ni equipos en almacén' : 'El almacén aún no tiene movimientos',
                detalle: 'Aparecen cuando el almacenero registra una entrada, un ingreso o un traslado desde el celular.' }}
              filtros={(
                <div className="d-flex flex-wrap gap-2 align-items-center">
                  <Segmentado etiqueta="Estado" valor={estado} enCambio={setEstado}
                    opciones={[{ valor: 'todos', texto: 'Todos' }, { valor: 'reponer', texto: `Por reponer (${r.porReponer})` }, { valor: 'sin_stock', texto: `Sin stock (${r.sinStock})` }]} />
                  {datos.almacenes.length > 1 && (
                    <select className="form-select w-auto" aria-label="Almacén" value={almacen} onChange={(e) => setAlmacen(e.target.value)}>
                      <option value="">Todos los almacenes</option>
                      {datos.almacenes.map((a) => <option key={a} value={a}>{a}</option>)}
                    </select>
                  )}
                </div>
              )}
              columnas={[
                { clave: 'insumo', titulo: 'Insumo', valor: (x) => x.descripcion,
                  celda: (x) => <><strong>{x.descripcion}</strong><div className="small text-body-secondary">{x.codigo}</div></> },
                { clave: 'cantidad', titulo: 'Cantidad', numerica: true, valor: (x) => x.cantidad,
                  celda: (x) => <><strong className="n">{cifra(x.cantidad)}</strong> <span className="small text-body-secondary">{x.unidad}</span>
                    {x.cantidadCompra != null && x.unidadCompra && x.unidadCompra !== x.unidad
                      && <div className="small text-body-secondary">≈ {cifra(x.cantidadCompra)} {x.unidadCompra}</div>}</> },
                { clave: 'donde', titulo: 'Dónde está', valor: (x) => dondeEsta(x, cifra), celda: (x) => <span className="small">{dondeEsta(x, cifra)}</span>, soloEscritorio: true },
                { clave: 'punto', titulo: 'Punto de reposición', numerica: true, valor: (x) => x.puntoReposicion,
                  celda: (x) => (x.puntoReposicion == null ? <span className="text-body-secondary">—</span>
                    : <span className="text-nowrap">{cifra(x.puntoReposicion)} {x.unidadCompra ?? ''}</span>), soloEscritorio: true },
                { clave: 'estado', titulo: 'Estado', valor: (x) => x.estado, celda: (x) => <Chip tono={TONO[x.estado]}>{TEXTO_ESTADO[x.estado]}</Chip> },
                ...(veCosto ? [{ clave: 'valor', titulo: 'Valor S/', numerica: true, valor: (x: Existencia) => x.valor,
                  celda: (x: Existencia) => <span className="n">{soles(x.valor)}</span>, soloEscritorio: true }] : []),
                { clave: 'ultimo', titulo: 'Último movimiento', numerica: true, valor: (x) => x.ultimoMovimiento,
                  celda: (x) => <span className="small text-nowrap">{fechaHora(x.ultimoMovimiento)}</span>, soloEscritorio: true },
              ]}
            />
          </Vidrio>
        </>
      )}
    </div>
  )
}
