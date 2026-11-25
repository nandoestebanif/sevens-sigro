/* ─────────────────────────────────────────────────────────────────────────────
   HU-58 · Como gerente, quiero consultar cuánto se gastó en los recursos de la
           obra: material, herramientas y horas hombre, para saber en qué estado
           está el dinero puesto en los recursos.
   T-325 (tablero con datos reales, reemplaza el prototipo) · T-326 (conexión)

   Criterios de la tarjeta y dónde se cumplen:
   - Comprado, recibido, consumido y en stock → cuatro tarjetas con el valor que
     calcula el servidor (Compras y el libro de Almacén, al promedio congelado).
   - Horas hombre, con las extra aparte → quinta tarjeta. Mientras no exista la
     tarifa por categoría (HU-65) se informan las horas sin valorizar, y la
     tarjeta lo dice en vez de inventar un monto.
   - Declara que es el gasto en recursos y no el costo total → franja de alcance
     con el texto que manda el servidor.
   - Cada cifra con su definición y su fecha de corte → debajo de cada valor. Las
     cinco comparten UN instante de corte, elegible (p. ej. el cierre de semana).
   Si las cifras no cuadran entre sí, el servidor lo marca y la pantalla lo
   muestra como «por conciliar» en lugar de presentar números contradictorios.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ErrorApi } from '../api/cliente'
import { fechaCorte, gasto, type Cifra, type ClaveCifra, type Gasto } from '../api/costo'
import { Chip } from '../ui/base'
import { Aviso, Ticket, TituloSeccion, Vidrio } from '../ui/piezas'
import type { Ilustracion } from '../ui/Ilustraciones'
import { GraficoFlujo } from '../ui/GraficosPerezosos'
import { Termino } from '../ui/Glosario'
import { EsqueletoKpis } from '../ui/Esqueleto'
import { Fecha } from '../ui/Fecha'
import { EnVivo, useActualizacion } from '../ui/Actualizacion'

const soles = (v: number) => v.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const horas = (v: number | null) => (v === null ? '—' : v.toLocaleString('es-PE', { maximumFractionDigits: 1 }))
const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })
const ROTULO: Record<ClaveCifra, string> = {
  comprado: 'Comprado', recibido: 'Recibido', consumido: 'Consumido', en_stock: 'En stock', horas_hombre: 'Horas hombre',
}
const FAMILIA: Record<string, string> = { material: 'Materiales', materiales: 'Materiales', equipo: 'Herramientas y equipos', herramienta: 'Herramientas y equipos', mano_obra: 'Horas hombre' }

const DIBUJO: Record<ClaveCifra, Ilustracion> = {
  comprado: 'factura', recibido: 'camion', consumido: 'cemento', en_stock: 'grua', horas_hombre: 'casco',
}

function Tarjeta({ c, g }: { c: Cifra; g: Gasto }) {
  const esHoras = c.clave === 'horas_hombre'
  return (
    <Ticket ilustracion={DIBUJO[c.clave]} etiqueta={ROTULO[c.clave]} estilo={{ flex: '1 1 190px', display: 'flex', flexDirection: 'column' }}>
      <span className="rotulo">{ROTULO[c.clave]}</span>
      {esHoras && c.valor === null ? (
        <span className="valor n">
          {horas(g.horas.normales)} h <span className="d-block fs-6 fw-semibold">+ {horas(g.horas.extra)} h extra</span>
        </span>
      ) : (
        <span className="valor n">{c.valor === null ? 'Sin dato' : `S/ ${soles(c.valor)}`}</span>
      )}
      <span className="pie">{c.definicion}</span>
      {c.nota && <span className="pie fw-semibold">{c.nota}</span>}
      <span className="pie" style={{ marginTop: 'auto', paddingTop: 6, fontSize: '0.72rem' }}>Corte: {fechaCorte(c.corte)}</span>
    </Ticket>
  )
}

export function GastoRecursos() {
  const [fecha, setFecha] = useState('')
  const [g, setG] = useState<Gasto | null>(null)
  const [error, setError] = useState('')
  // En vivo: con el corte de hoy, el gasto se recalcula solo (cada 30 s y al volver a la pestaña)
  const vivo = useActualizacion(() => gasto(fecha || undefined).then((x) => { setG(x); setError('') }).catch((e) => setError(
    e instanceof ErrorApi && e.codigo === 'sin_permiso' ? 'El gasto de la obra lo consultan el residente, el administrador y el gerente.'
      : e instanceof ErrorApi ? e.message : 'No se pudo consultar el gasto de la obra.')), { activo: !fecha })
  const primera = useRef(true)
  useEffect(() => {
    if (primera.current) { primera.current = false; return }
    setG(null); vivo.actualizar()   // otra fecha de corte: se consulta de nuevo
  }, [fecha]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="d-flex flex-column gap-3">
      {/* el propio tablero declara su alcance (CLAUDE.md): a la vista, pero en una línea */}
      <p className="small text-body-secondary mb-0" role="note">
        <strong className="text-body">Gasto en recursos, no costo total de la obra:</strong>{' '}
        {g?.declaracion ?? 'quedan fuera gastos generales, subcontratos, servicios y planilla.'}
      </p>
      {!fecha && <div><EnVivo {...vivo} /></div>}

      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }} etiqueta="Gasto en recursos">
        <div className="d-flex flex-wrap justify-content-between align-items-end gap-3">
          <div className="d-flex flex-column gap-1">
            <TituloSeccion>Dónde está el dinero puesto en recursos</TituloSeccion>
            <span className="small text-body-secondary">
              Las cinco cifras se cortan en el mismo instante: {g ? <strong>{fechaCorte(g.corte)}</strong> : '…'} (hora de Lima)
            </span>
          </div>
          <div className="d-flex align-items-center gap-2 small fw-semibold text-body-secondary">
            <span className="text-nowrap">Fecha de corte</span>
            <div style={{ width: 190 }}><Fecha id="gasto-corte" etiqueta="Fecha de corte del gasto" valor={fecha} max={hoyLima()} alCambiar={setFecha} /></div>
            {fecha && <button type="button" onClick={() => setFecha('')}
              className="btn btn-outline-secondary btn-sm">Ahora</button>}
          </div>
        </div>

        {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
        {!g && !error && <EsqueletoKpis cuantas={5} />}
        {g && (
          <>
            {g.porConciliar && (
              <Aviso tono="aviso" rol="status">
                <strong><Termino clave="por conciliar">Cifras por conciliar</Termino>:</strong> no se presentan como definitivas.
                <ul className="ps-3" style={{ margin: '4px 0 0' }}>{g.conciliacion.map((t) => <li key={t}>{t}</li>)}</ul>
              </Aviso>
            )}
            <div className="d-flex flex-wrap gap-3">
              {g.cifras.map((c) => <Tarjeta key={c.clave} c={c} g={g} />)}
            </div>
            {g.otrosMovimientos !== null && g.otrosMovimientos !== 0 && (
              <p className="small text-body-secondary">
                Traslados entre obras y ajustes de conteo: <strong>S/ {soles(g.otrosMovimientos)}</strong>. También mueven el stock:
                recibido − consumido + estos movimientos = en stock.
              </p>
            )}
            {g.porFamilia.length > 0 && (
              <GraficoFlujo familias={g.porFamilia.map((f) => ({ etiqueta: FAMILIA[f.familia] ?? f.familia, recibido: f.recibido, consumido: f.consumido, enStock: f.enStock }))} />
            )}
            {g.porFamilia.length > 0 && (
              <div className="overflow-x-auto">
                <table className="table table-hover align-middle mb-0">
                  <thead><tr><th className="text-start">Familia de recurso</th><th className="n">Recibido</th><th className="n">Consumido</th><th className="n">En stock</th><th className="n">Último hecho</th></tr></thead>
                  <tbody>
                    {g.porFamilia.map((f) => (
                      <tr key={f.familia}>
                        <td><strong>{FAMILIA[f.familia] ?? f.familia}</strong></td>
                        <td className="n">S/ {soles(f.recibido)}</td><td className="n">S/ {soles(f.consumido)}</td>
                        <td className="n">S/ {soles(f.enStock)}</td><td className="n">{f.hasta ? f.hasta.split('-').reverse().join('/') : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="d-flex flex-wrap gap-2 align-items-center">
              <Chip tono="neutro">{g.criterio}</Chip>
              <Link to="/inventario" className="small fw-semibold text-primary">Ver el inventario valorizado →</Link>
            </div>
          </>
        )}
      </Vidrio>
    </div>
  )
}
