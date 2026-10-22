/* SIGRO · armazón de la web de oficina: barra lateral, obra activa y rutas. */
import { useEffect, useState } from 'react'
import { HashRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { api } from './api/cliente'
import type { ObraActiva } from './api/tipos'
import { Catalogo } from './paginas/Catalogo'
import { Compras } from './paginas/Compras'
import { Importar } from './paginas/Importar'
import { Movimientos } from './paginas/Movimientos'
import { Proveedores } from './paginas/Proveedores'
import { Reposicion } from './paginas/Reposicion'
import { Tablero } from './paginas/Tablero'

/* Íconos del menú: trazo simple, 16 px, heredan el color del texto. */
const Icono = ({ d }: { d: string }) => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ flex: 'none' }}>
    {d.split('|').map((seg, i) => <path key={i} d={seg} />)}
  </svg>
)
const ICONOS = {
  tablero: 'M3 3v18h18|M7 15v-4|M12 15V7|M17 15v-7',
  reposicion: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9|M10.3 21a1.94 1.94 0 0 0 3.4 0',
  compras: 'M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z|M3 6h18|M16 10a4 4 0 0 1-8 0',
  proveedores: 'M5 18H3V6h12v12H9|M19 18h2v-5l-3-4h-4v9h1|M7.5 20a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3|M17.5 20a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3',
  movimientos: 'M8 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2h-3|M12 17V7|M8 10l4-4 4 4',
  catalogo: 'M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z|M3.3 7 12 12l8.7-5|M12 22V12',
  importar: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4|M7 8l5-5 5 5|M12 3v12',
}

const GRUPOS: { rotulo: string; items: { ruta: string; nombre: string; icono: keyof typeof ICONOS }[] }[] = [
  {
    // la importación del S10 es parte de PREPARAR la obra (se hace al crearla);
    // aquí vivirán también «Crear la obra» (HU-01) y sus parámetros
    rotulo: 'Preparar la obra',
    items: [{ ruta: '/importar', nombre: 'Importar del S10', icono: 'importar' }],
  },
  {
    rotulo: 'Control de la obra',
    items: [
      // «gasto en recursos», nunca «costo de obra»: el sistema no informa el
      // costo total (gastos generales, subcontratos y planilla quedan fuera)
      { ruta: '/tablero', nombre: 'Gasto en recursos', icono: 'tablero' },
      { ruta: '/reposicion', nombre: 'Reposición', icono: 'reposicion' },
    ],
  },
  {
    rotulo: 'Abastecimiento',
    items: [
      { ruta: '/compras', nombre: 'Compras', icono: 'compras' },
      { ruta: '/proveedores', nombre: 'Proveedores', icono: 'proveedores' },
    ],
  },
  {
    rotulo: 'Almacén',
    items: [{ ruta: '/movimientos', nombre: 'Ingresos y salidas', icono: 'movimientos' }],
  },
  {
    rotulo: 'Catálogo',
    items: [{ ruta: '/catalogo', nombre: 'Catálogo de insumos', icono: 'catalogo' }],
  },
]

/* Panel de accesibilidad (abajo a la derecha): tema claro/oscuro y escala del
   texto. Todo el estilo vive en CSS variables y la escala es un zoom del body,
   así que ambos ajustes son un atributo en <html> y nada más. Persisten en el
   dispositivo (si no hay almacenamiento, simplemente no persisten). */
const ESCALAS = [
  { valor: '90', rotulo: '90 %' },
  { valor: '100', rotulo: '100 %' },
  { valor: '110', rotulo: '110 %' },
  { valor: '125', rotulo: '125 %' },
]

function PanelAccesibilidad() {
  const [abierto, setAbierto] = useState(false)
  const [tema, setTema] = useState(() => {
    try { return localStorage.getItem('sigro-tema') ?? 'claro' } catch { return 'claro' }
  })
  const [escala, setEscala] = useState(() => {
    try { return localStorage.getItem('sigro-escala') ?? '100' } catch { return '100' }
  })
  useEffect(() => {
    document.documentElement.dataset.tema = tema
    document.documentElement.dataset.escala = escala
    try {
      localStorage.setItem('sigro-tema', tema)
      localStorage.setItem('sigro-escala', escala)
    } catch { /* sin almacenamiento, los ajustes no persisten */ }
  }, [tema, escala])

  const opcion = (activo: boolean, rotulo: string, alClic: () => void, titulo?: string) => (
    <button
      key={rotulo}
      type="button"
      title={titulo}
      onClick={alClic}
      style={{
        flex: 1, padding: '7px 0', fontSize: 12.5, fontWeight: 600, cursor: 'pointer',
        fontFamily: 'inherit', borderRadius: 'var(--radio-chico)',
        border: `1px solid ${activo ? 'var(--accion)' : 'var(--borde)'}`,
        background: activo ? 'var(--marca-tinte)' : 'var(--tarjeta)',
        color: activo ? 'var(--marca-tinte-tinta)' : 'var(--tinta-2)',
      }}
    >
      {rotulo}
    </button>
  )

  return (
    <div style={{ position: 'fixed', right: 18, bottom: 18, zIndex: 60, display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 10 }}>
      {abierto && (
        <div
          role="dialog"
          aria-label="Ajustes de pantalla"
          style={{
            width: 240, background: 'var(--tarjeta)', border: '1px solid var(--borde)',
            borderRadius: 'var(--radio)', boxShadow: 'var(--sombra), 0 18px 44px rgba(20,20,20,0.18)',
            padding: 16, display: 'flex', flexDirection: 'column', gap: 14,
          }}
        >
          <div style={{ fontSize: 13.5, fontWeight: 700 }}>Pantalla</div>
          <div>
            <div style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--tinta-3)', marginBottom: 6 }}>Tema</div>
            <div style={{ display: 'flex', gap: 7 }}>
              {opcion(tema === 'claro', 'Claro', () => setTema('claro'))}
              {opcion(tema === 'oscuro', 'Oscuro', () => setTema('oscuro'))}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--tinta-3)', marginBottom: 6 }}>Tamaño del texto</div>
            <div style={{ display: 'flex', gap: 7 }}>
              {ESCALAS.map((e) => opcion(escala === e.valor, e.rotulo, () => setEscala(e.valor), `Texto al ${e.rotulo}`))}
            </div>
          </div>
          <div style={{ fontSize: 11, color: 'var(--tinta-3)', lineHeight: 1.45 }}>
            Los ajustes se guardan en este dispositivo.
          </div>
        </div>
      )}
      <button
        type="button"
        aria-label={abierto ? 'Cerrar ajustes de pantalla' : 'Abrir ajustes de pantalla: tema y tamaño del texto'}
        aria-expanded={abierto}
        title="Accesibilidad: tema y tamaño del texto"
        onClick={() => setAbierto(!abierto)}
        style={{
          width: 44, height: 44, borderRadius: 99, border: '1px solid var(--borde)',
          background: 'var(--tarjeta)', boxShadow: 'var(--sombra)', cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        {abierto ? (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--tinta-2)" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
            <path d="M6 6l12 12" /><path d="M18 6L6 18" />
          </svg>
        ) : (
          <span aria-hidden="true" style={{ fontSize: 15, fontWeight: 700, color: 'var(--tinta-2)', letterSpacing: '-0.5px' }}>
            Aa
          </span>
        )}
      </button>
    </div>
  )
}

export default function App() {
  const [obra, setObra] = useState<ObraActiva | null>(null)
  useEffect(() => {
    api.obraActiva().then(setObra)
  }, [])

  return (
    <HashRouter>
      <div style={{ display: 'flex', minHeight: '100%' }}>
        <aside
          style={{
            width: 228, padding: '22px 14px', display: 'flex', flexDirection: 'column',
            gap: 22, borderRight: '1px solid var(--borde)', background: 'var(--tarjeta)',
            position: 'sticky', top: 0, height: '100vh',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '0 13px' }}>
            <div
              aria-hidden
              style={{
                width: 32, height: 32, borderRadius: 10, background: 'var(--marca)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: '#fff', fontWeight: 800, fontSize: 15,
              }}
            >
              S
            </div>
            <div style={{ fontSize: 17, fontWeight: 700, letterSpacing: '-0.3px' }}>SIGRO</div>
          </div>

          <nav style={{ display: 'flex', flexDirection: 'column', gap: 14, overflowY: 'auto' }}>
            {GRUPOS.map((g) => (
              <div key={g.rotulo}>
                <div style={{ padding: '0 13px 5px', fontSize: 10.5, fontWeight: 700, letterSpacing: 0.8, textTransform: 'uppercase', color: 'var(--tinta-3)' }}>
                  {g.rotulo}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {g.items.map((s) => (
                    <NavLink
                      key={s.ruta}
                      to={s.ruta}
                      style={({ isActive }) => ({
                        display: 'flex', alignItems: 'center', gap: 10,
                        padding: '8px 13px', borderRadius: 'var(--radio-chico)',
                        fontSize: 13.5, textDecoration: 'none',
                        fontWeight: isActive ? 700 : 500,
                        color: isActive ? 'var(--marca-tinte-tinta)' : 'var(--tinta-3)',
                        background: isActive ? 'var(--marca-tinte)' : 'transparent',
                      })}
                    >
                      <Icono d={ICONOS[s.icono]} />
                      {s.nombre}
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>

          <div
            style={{
              marginTop: 'auto', background: 'var(--fondo)', border: '1px solid var(--borde)',
              borderRadius: 14, padding: 13,
            }}
          >
            <div style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>Obra activa</div>
            <div style={{ fontSize: 13.5, fontWeight: 600, marginTop: 3 }}>
              {obra?.nombre ?? '…'}
            </div>
            <div className="n" style={{ fontSize: 11.5, color: 'var(--tinta-3)', marginTop: 3 }}>
              {obra ? `semana ${obra.semana} de ${obra.semanasTotales}` : ''}
            </div>
          </div>
        </aside>

        <main style={{ flex: 1, minWidth: 0, padding: '24px 28px' }}>
          <div style={{ maxWidth: 1180, margin: '0 auto' }}>
          <Routes>
            <Route path="/" element={<Navigate to="/proveedores" replace />} />
            <Route path="/proveedores" element={<Proveedores />} />
            <Route path="/compras" element={<Compras />} />
            <Route path="/tablero" element={<Tablero />} />
            <Route path="/reposicion" element={<Reposicion />} />
            <Route path="/catalogo" element={<Catalogo />} />
            <Route path="/movimientos" element={<Movimientos />} />
            <Route path="/importar" element={<Importar />} />
          </Routes>
          </div>
        </main>
        <PanelAccesibilidad />
      </div>
    </HashRouter>
  )
}
