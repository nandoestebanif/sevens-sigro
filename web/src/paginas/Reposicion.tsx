/* ─────────────────────────────────────────────────────────────────────────────
   HU-24 · Como residente de obra, quiero consultar cuánto falta comprar de cada
           insumo, para formular el siguiente requerimiento sobre una base cierta.

   Criterios de la tarjeta:
   - Falta por comprar = requerido − comprado → columna central, y lo calcula la
     capa de API (la UI no hace aritmética de negocio).
   - Se puede filtrar por fase y por insumo crítico → chips y selector.
   - La consulta se exporta para armar el pedido → botón que baja un CSV con lo
     filtrado, listo para convertirse en la solicitud de materiales (HU-66).
   Lienzo W5 «Reposición» (estilo vidrio).
   ───────────────────────────────────────────────────────────────────────── */
import { reposicionHttp, usaApiReal } from '../api/http'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Tab, Tabs } from 'react-bootstrap'
import { api } from '../api/cliente'
import { ErrorApi, mensajeDeCarga } from '../api/error'
import type { AlertaReposicion, AvanceInsumo } from '../api/tipos'
import { Chip, Vacio } from '../ui/base'
import { Chips, Encabezado, Pildora, Segmentado, TituloSeccion, Vidrio } from '../ui/piezas'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { AccionesFila, TablaDatos } from '../ui/TablaDatos'
import { puedo } from '../api/permisos'

const n2 = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })

/* ── HU-26 · HU-27: el aviso salta solo y nunca va solo — trae saldo, punto,
   falta por comprar y la sugerencia editable con su criterio ── */
function Alerta({ a }: { a: AlertaReposicion }) {
  // HU-27: referencia EDITABLE, nunca pedido automático
  const [cantidad, setCantidad] = useState(String(a.sugerencia))
  const activa = a.estado === 'activa'
  return (
    <Vidrio estilo={{ borderLeft: `4px solid ${activa ? 'var(--grave)' : 'var(--aviso)'}`, padding: '16px 20px' }} etiqueta={`Aviso de reposición: ${a.descripcion}`}>
      <div className="d-flex justify-content-between gap-3 align-items-baseline flex-wrap">
        <span className="d-inline-flex align-items-center gap-2 flex-wrap">
          <strong>{a.descripcion}</strong>
          {activa
            ? <Chip tono="grave">bajo el punto de reposición</Chip>
            : <Chip tono="aviso">atendida · compra {a.atendidaPor}</Chip>}
        </span>
        <span className="n small text-body-secondary">
          saldo <strong>{n2(a.saldo)} {a.unidad}</strong> · punto {n2(a.punto)} · falta por comprar {n2(a.faltaPorComprar)}
        </span>
      </div>
      <div className="small text-body-secondary mt-1">
        punto configurado como {a.criterioPunto}
      </div>
      <div className="d-flex align-items-center gap-2 mt-3 flex-wrap">
        <label htmlFor={`sug-${a.insumoId}`} className="small fw-semibold text-body-secondary">
          Sugerencia de compra:
        </label>
        <input
          id={`sug-${a.insumoId}`}
          className="n form-control fw-bold text-end"
          value={cantidad}
          onChange={(e) => setCantidad(e.target.value)}
          style={{ width: 110, height: 44 }}
        />
        <span className="small text-body-secondary">{a.unidad}</span>
        <span className="small text-body-secondary" style={{ flex: '1 1 260px' }}>
          referencia editable, nunca pedido automático · criterio: {a.criterioSugerencia}
        </span>
      </div>
    </Vidrio>
  )
}

export function Reposicion() {
  const [filas, setFilas] = useState<AvanceInsumo[] | null>(null)
  const [alertas, setAlertas] = useState<AlertaReposicion[]>([])
  const [soloCriticos, setSoloCriticos] = useState(false)
  const [fase, setFase] = useState('todas')
  // fase 1 del rediseño: tres pestañas en vez de una página larga (lo de abajo ya no queda escondido)
  const [pestana, setPestana] = useState<string | null>(null)

  const [fallo, setFallo] = useState('')
  const [avisoCritico, setAvisoCritico] = useState<{ codigo: string; texto: string } | null>(null)
  // HU-28: solo se ofrece marcar si el servidor ya tiene /criticos (null = aún no desplegado); sin eso el botón no podría guardar
  const [conHu28, setConHu28] = useState(false)
  const puedeMarcar = puedo('critico.marcar')
  const [candidatos, setCandidatos] = useState<{ codigo: string; descripcion: string; costo: number; pct: number; critico: boolean }[]>([])
  useEffect(() => { if (usaApiReal && puedeMarcar) reposicionHttp.candidatosCriticos().then(setCandidatos).catch(() => setCandidatos([])) }, [puedeMarcar])
  useEffect(() => { if (usaApiReal) reposicionHttp.criticos().then((x) => setConHu28(x !== null)).catch(() => setConHu28(false)) }, [])
  // HU-28: marcar o quitar «crítico» (residente y administrador; el servidor decide el permiso)
  const alternarCritico = async (f: AvanceInsumo) => {
    setAvisoCritico({ codigo: f.codigo, texto: 'Guardando…' })
    try {
      await reposicionHttp.marcarCritico(f.codigo, !f.critico)
      setFilas((xs) => (xs ?? []).map((x) => (x.codigo === f.codigo ? { ...x, critico: !f.critico } : x)))
      setAvisoCritico({ codigo: f.codigo, texto: f.critico ? 'Ya no es crítico.' : 'Marcado como crítico: aparece en «Solo críticos».' })
    } catch (e) {
      setAvisoCritico({ codigo: f.codigo, texto: e instanceof ErrorApi && e.estado === 403 ? 'Solo el residente o el administrador marcan los insumos críticos.'
        : e instanceof ErrorApi && e.estado === 404 ? 'Marcar críticos estará disponible cuando el servidor tenga HU-28.'
          : e instanceof ErrorApi ? e.message : 'No se pudo cambiar la marca. Inténtalo de nuevo.' })
    }
  }
  useEffect(() => {
    api.saldoPorComprar().then(setFilas).catch((x) => { setFilas([]); setFallo(mensajeDeCarga(x)) })
    api.alertasReposicion().then(setAlertas).catch(() => setAlertas([]))
  }, [])

  const fases = useMemo(
    () => Array.from(new Set((filas ?? []).map((f) => f.fase))),
    [filas],
  )
  const visibles = useMemo(() => (filas ?? [])
    .filter((f) => (!soloCriticos || f.critico) && (fase === 'todas' || f.fase === fase))
    .filter((f) => f.saldoPorComprar > 0), [filas, soloCriticos, fase])

  // seguridad: un texto que empieza con = + - @ se neutraliza con apóstrofo,
  // para que Excel no lo ejecute como fórmula al abrir el CSV
  const celdaSegura = (v: string) => `"${(/^[=+\-@]/.test(v) ? `'${v}` : v).replace(/"/g, '""')}"`

  const exportar = () => {
    const cab = 'codigo,insumo,fase,critico,unidad_compra,requerido,comprado,falta_por_comprar'
    const cuerpo = visibles.map((f) =>
      [f.codigo, celdaSegura(f.descripcion), f.fase, f.critico ? 'si' : 'no', f.unidadCompra,
        f.planificadoCantidad, f.compradoCantidad, f.saldoPorComprar].join(','))
    const blob = new Blob([[cab, ...cuerpo].join('\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `saldo-por-comprar-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return (
    <>
      <div className="d-flex flex-column gap-3 pb-4">
        <Encabezado
          titulo="Reposición"
          sub="Qué falta comprar de lo planificado y qué está por acabarse en la obra"
          acciones={
            <>
              <Link to="/parametros" className="btn btn-outline-secondary">Parámetros de reposición</Link>
              <Pildora enClic={exportar} deshabilitado={!visibles.length}>Exportar para el pedido</Pildora>
            </>
          }
        />

        <Chips>
          {usaApiReal
            ? <Chip tono="bien">Requerido, comprado y stock: del servidor</Chip>
            : <Chip tono="info">Comprado y stock: datos de ejemplo · lo requerido es el S10 real</Chip>}
          <Chip tono="neutro">El requerimiento avisa, no bloquea</Chip>
          <Chip tono="neutro">Falta por comprar = requerido − comprado, en unidad de compra (el acero en varillas aunque el S10 lo planifique en kilos)</Chip>
          <Chip tono="neutro">La sugerencia de compra (HU-27) es el menor entre lo que falta y lo que exige la fase en curso</Chip>
        </Chips>

        <Tabs id="reposicion-pestanas" activeKey={pestana ?? (alertas.some((a) => a.estado === 'activa') ? 'alertas' : 'saldo')}
          onSelect={(k) => setPestana(k)} className="sigro-pestanas" mountOnEnter>
          <Tab eventKey="alertas" title={<>Alertas <span className="badge rounded-pill text-bg-danger ms-1">{alertas.filter((a) => a.estado === 'activa').length}</span></>}>
            <div className="d-flex flex-column gap-3 pt-3">
              {alertas.length === 0
                ? <Vidrio><Vacio titulo="Sin alertas" detalle="Ningún insumo está bajo su punto de reposición. Los puntos se definen en «Parámetros de reposición»." /></Vidrio>
                : alertas.map((a) => <Alerta key={a.insumoId} a={a} />)}
            </div>
          </Tab>
          <Tab eventKey="saldo" title={<>Saldo por comprar <span className="badge rounded-pill text-bg-secondary ms-1">{visibles.length}</span></>}>
        <div className="pt-3">
        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }} etiqueta="Saldo por comprar">
          <div className="d-flex flex-column gap-1">
            <TituloSeccion>Saldo por comprar</TituloSeccion>

          </div>
          <div className="d-flex flex-wrap gap-2 align-items-center">
            <Segmentado
              etiqueta="Filtro por criticidad" valor={soloCriticos} enCambio={setSoloCriticos}
              opciones={[{ valor: false, texto: 'Todos' }, { valor: true, texto: 'Solo críticos' }]}
            />
            {/* D14: las fases llegan con el requerimiento por fase (HU-20, Product Backlog); con una sola no hay nada que elegir */}
            {fases.length > 1 && <select
              aria-label="Filtrar por fase"
              value={fase}
              onChange={(e) => setFase(e.target.value)}
              className="form-select fw-semibold rounded-pill"
               style={{ width: 'auto', minWidth: 200, height: 46 }}
            >
              <option value="todas">Todas las fases</option>
              {fases.map((f) => <option key={f} value={f}>{f}</option>)}
            </select>}
          </div>
          {soloCriticos && (
            <span className="small text-body-secondary">
              {conHu28
                ? <>Los críticos los marca el residente con «Marcar crítico» (HU-28).</>
                : <>Crítico = insumo con punto de reposición definido en <Link to="/parametros">Parámetros de reposición</Link>.</>}
            </span>
          )}

          {filas === null ? <EsqueletoTabla /> : fallo ? (
            <Vacio titulo="No se pudo cargar el saldo por comprar" detalle={fallo} />
          ) : soloCriticos && !(filas ?? []).some((f) => f.critico) ? (
            <Vacio titulo="Todavía no hay insumos críticos" detalle={conHu28 ? 'Márcalos abajo, entre los que el sistema sugiere por su peso en el presupuesto.' : 'Define el stock de seguridad y el punto de reposición del insumo en «Parámetros de reposición» y aparecerá aquí.'} />
          ) : (
            <TablaDatos
              etiqueta="Saldo por comprar"
              filas={visibles}
              clave={(f) => f.insumoId}
              buscar={(f) => `${f.descripcion} ${f.codigo}`}
              marcadorBusqueda="Buscar insumo…"
              ordenInicial={{ clave: 'falta', dir: 'desc' }}
              vacio={{ titulo: 'Nada pendiente con este filtro', detalle: 'Todos los insumos filtrados ya tienen su compra completa.' }}
              columnas={[
                { clave: 'insumo', titulo: 'Insumo', valor: (f) => f.descripcion, celda: (f) => (
                  <>
                    <div className="d-flex align-items-center gap-2 flex-wrap">
                      <span className="fw-semibold">{f.descripcion}</span>
                      {f.critico && <Chip tono="aviso">crítico</Chip>}
                    </div>
                    <div className="n small text-body-secondary">SKU {f.codigo}{fases.length > 1 ? ` · ${f.fase}` : ''}</div>
                    {avisoCritico?.codigo === f.codigo && <div role="status" className="small text-body-secondary mt-1">{avisoCritico.texto}</div>}
                  </>
                ) },
                { clave: 'requerido', titulo: 'Requerido (S10)', numerica: true, valor: (f) => f.planificadoCantidad, celda: (f) => <span className="text-nowrap">{n2(f.planificadoCantidad)} {f.unidadS10}</span> },
                { clave: 'comprado', titulo: 'Comprado', numerica: true, valor: (f) => f.compradoPct, celda: (f) => <span className="text-nowrap">{n2(f.compradoCantidad)} {f.unidadS10} · {f.compradoPct} %</span> },
                { clave: 'falta', titulo: 'Falta por comprar', numerica: true, valor: (f) => f.saldoPorComprar, celda: (f) => <strong className="text-nowrap">{n2(f.saldoPorComprar)} {f.unidadCompra}</strong> },
              ]}
              acciones={conHu28 && puedeMarcar ? (f) => (
                <AccionesFila de={f.descripcion} acciones={[{ texto: f.critico ? 'Quitar crítico' : '★ Marcar crítico', enClic: () => void alternarCritico(f) }]} />
              ) : undefined}
            />
          )}
        </Vidrio>
        </div>
          </Tab>

        {conHu28 && puedeMarcar && candidatos.length > 0 && (
          <Tab eventKey="sugeridos" title={<>Sugeridos como críticos <span className="badge rounded-pill text-bg-secondary ms-1">{candidatos.length}</span></>}>
          <div className="pt-3">
          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 10 }} etiqueta="Críticos sugeridos">
            <div>
              <TituloSeccion>Sugeridos como críticos</TituloSeccion>
              <span className="small text-body-secondary">Los que más pesan en el presupuesto. El sistema sugiere; tú decides.</span>
            </div>
            <TablaDatos
              etiqueta="Críticos sugeridos"
              filas={candidatos}
              clave={(c) => c.codigo}
              vacio={{ titulo: 'Sin sugerencias', detalle: 'Importa el requerimiento del S10.' }}
              columnas={[
                { clave: 'insumo', titulo: 'Insumo', celda: (c) => <><strong>{c.descripcion}</strong> {c.critico && <Chip tono="aviso">crítico</Chip>}<div className="n small text-body-secondary">SKU {c.codigo}</div></> },
                { clave: 'costo', titulo: 'Presupuestado', numerica: true, celda: (c) => <span className="text-nowrap">S/ {c.costo.toLocaleString('es-PE', { maximumFractionDigits: 0 })}</span> },
                { clave: 'pct', titulo: '% del total', numerica: true, celda: (c) => `${c.pct.toLocaleString('es-PE')} %` },
              ]}
              acciones={(c) => (
                <AccionesFila de={c.descripcion} acciones={[{ texto: c.critico ? 'Quitar crítico' : '★ Marcar crítico', enClic: async () => {
                  await reposicionHttp.marcarCritico(c.codigo, !c.critico).catch(() => undefined)
                  setCandidatos((xs) => xs.map((x) => x.codigo === c.codigo ? { ...x, critico: !c.critico } : x))
                  setFilas((xs) => (xs ?? []).map((x) => (x.codigo === c.codigo ? { ...x, critico: !c.critico } : x)))
                } }]} />
              )}
            />
          </Vidrio>
          </div>
          </Tab>
        )}
        </Tabs>

      </div>
    </>
  )
}
