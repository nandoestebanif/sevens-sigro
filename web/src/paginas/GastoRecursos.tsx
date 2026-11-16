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
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ErrorApi } from '../api/cliente'
import { fechaCorte, gasto, type Cifra, type ClaveCifra, type Gasto } from '../api/costo'
import { Chip, Vacio } from '../ui/base'
import { Aviso, TituloSeccion, Vidrio } from '../ui/vidrio'

const soles = (v: number) => v.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const horas = (v: number | null) => (v === null ? '—' : v.toLocaleString('es-PE', { maximumFractionDigits: 1 }))
const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })
const ROTULO: Record<ClaveCifra, string> = {
  comprado: 'Comprado', recibido: 'Recibido', consumido: 'Consumido', en_stock: 'En stock', horas_hombre: 'Horas hombre',
}
const FAMILIA: Record<string, string> = { material: 'Materiales', materiales: 'Materiales', equipo: 'Herramientas y equipos', herramienta: 'Herramientas y equipos', mano_obra: 'Horas hombre' }

function Tarjeta({ c, g }: { c: Cifra; g: Gasto }) {
  const esHoras = c.clave === 'horas_hombre'
  return (
    <div style={{ flex: '1 1 200px', minWidth: 0, background: 'var(--vidrio-celda)', borderRadius: 18, padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--tinta-2)' }}>{ROTULO[c.clave]}</span>
      {esHoras && c.valor === null ? (
        <span className="n" style={{ fontSize: 22, fontWeight: 800 }}>
          {horas(g.horas.normales)} h <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--tinta-2)' }}>+ {horas(g.horas.extra)} h extra</span>
        </span>
      ) : (
        <span className="n" style={{ fontSize: 24, fontWeight: 800 }}>{c.valor === null ? 'Sin dato' : `S/ ${soles(c.valor)}`}</span>
      )}
      <span style={{ fontSize: 12.5, color: 'var(--tinta-2)', lineHeight: 1.45 }}>{c.definicion}</span>
      {c.nota && <span style={{ fontSize: 12.5, color: 'var(--aviso)', fontWeight: 600, lineHeight: 1.45 }}>{c.nota}</span>}
      <span style={{ fontSize: 12, color: 'var(--tinta-2)', marginTop: 'auto' }}>Corte: {fechaCorte(c.corte)}</span>
    </div>
  )
}

export function GastoRecursos() {
  const [fecha, setFecha] = useState('')
  const [g, setG] = useState<Gasto | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    setG(null); setError('')
    gasto(fecha || undefined).then(setG).catch((e) => setError(
      e instanceof ErrorApi && e.codigo === 'sin_permiso' ? 'El gasto de la obra lo consultan el residente, el administrador y el gerente.'
        : e instanceof ErrorApi ? e.message : 'No se pudo consultar el gasto de la obra.'))
  }, [fecha])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Vidrio estilo={{ borderLeft: '4px solid var(--accion)', padding: '14px 20px' }} etiqueta="Alcance del reporte">
        <p style={{ fontSize: 14, color: 'var(--tinta-2)', lineHeight: 1.55 }}>
          <strong style={{ color: 'var(--tinta)' }}>Esto es el gasto en recursos, no el costo total de la obra.</strong>{' '}
          {g?.declaracion ?? 'Quedan fuera los gastos generales, los subcontratos, los servicios y la planilla.'}
        </p>
      </Vidrio>

      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }} etiqueta="Gasto en recursos">
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <TituloSeccion>Dónde está el dinero puesto en recursos</TituloSeccion>
            <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>
              Las cinco cifras se cortan en el mismo instante: {g ? <strong>{fechaCorte(g.corte)}</strong> : '…'} (hora de Lima)
            </span>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)' }}>
            Fecha de corte
            <input type="date" className="campo" aria-label="Fecha de corte del gasto" value={fecha} max={hoyLima()}
              onChange={(e) => setFecha(e.target.value)} style={{ height: 40, padding: '0 10px', fontSize: 14, borderRadius: 12 }} />
            {fecha && <button type="button" className="foco-vidrio" onClick={() => setFecha('')}
              style={{ height: 40, padding: '0 12px', borderRadius: 20, border: '1px solid var(--separador)', background: 'transparent', color: 'var(--tinta)', fontWeight: 600, cursor: 'pointer' }}>Ahora</button>}
          </label>
        </div>

        {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
        {!g && !error && <Vacio titulo="Consultando…" detalle="Leyendo compras, el libro de almacén y las horas registradas." />}
        {g && (
          <>
            {g.porConciliar && (
              <Aviso tono="aviso" rol="status">
                <strong>Cifras por conciliar:</strong> no se presentan como definitivas.
                <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>{g.conciliacion.map((t) => <li key={t}>{t}</li>)}</ul>
              </Aviso>
            )}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
              {g.cifras.map((c) => <Tarjeta key={c.clave} c={c} g={g} />)}
            </div>
            {g.otrosMovimientos !== null && g.otrosMovimientos !== 0 && (
              <p style={{ fontSize: 13, color: 'var(--tinta-2)' }}>
                Traslados entre obras y ajustes de conteo: <strong>S/ {soles(g.otrosMovimientos)}</strong>. También mueven el stock:
                recibido − consumido + estos movimientos = en stock.
              </p>
            )}
            {g.porFamilia.length > 0 && (
              <div style={{ overflowX: 'auto' }}>
                <table className="tabla-vidrio">
                  <thead><tr><th style={{ textAlign: 'left' }}>Familia de recurso</th><th className="n">Recibido</th><th className="n">Consumido</th><th className="n">En stock</th><th className="n">Último hecho</th></tr></thead>
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
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
              <Chip tono="neutro">{g.criterio}</Chip>
              <Link to="/inventario" className="foco-vidrio" style={{ fontSize: 13, fontWeight: 600, color: 'var(--accion)' }}>Ver el inventario valorizado →</Link>
            </div>
          </>
        )}
      </Vidrio>
    </div>
  )
}
