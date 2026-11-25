/* SIGRO · armazón de la web de oficina con React-Bootstrap: Navbar arriba, menú lateral (Offcanvas + Accordion), obra activa y rutas. */
import { CambiarClave, Campana } from './ui/Cuenta'
import { puede } from './api/permisos'
import { Component, Suspense, lazy, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { HashRouter, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { grupoDeRuta } from './menu'
import { api } from './api/cliente'
import { cerrarSesion, sesionActual, usaApiReal } from './api/http'
import { ElegirObra } from './paginas/ElegirObra'
import { Entrar } from './paginas/Entrar'
import { Arranque, CapaPlano, useInerte } from './ui/Arranque'
import {  } from './ui/piezas'
import { esAdministrador } from './paginas/administracion/reglas'
import type { Obra, ObraActiva } from './api/tipos'
import logoBlanco from './assets/sevens-logo-blanco.webp'
import { Accordion, Alert, Badge, Button, Container, Form, Nav, NavDropdown, Offcanvas } from 'react-bootstrap'
import { EsqueletoPagina } from './ui/Esqueleto'
import { useInstalar } from './ui/instalar'

const ROLES: Record<string, string> = {
  residente: 'Residente de obra', administrador: 'Administrador', gerente: 'Gerente',
  almacenero: 'Almacenero', ssoma: 'Ingeniero SSOMA',
}

/* Guardián de errores del frontend: si una pantalla revienta por un error no
   previsto, la persona ve esta tarjeta y el resto de la web sigue vivo — nunca
   una página en blanco. Los errores PREVISTOS no llegan aquí: los maneja cada
   pantalla con los mensajes de la capa de API (ErrorApi). */
class GuardianErrores extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <Alert variant="danger">
        <Alert.Heading as="h2" className="h6">Esta pantalla tuvo un problema</Alert.Heading>
        <p className="small mb-2">
          No se perdió nada: los registros viven en el servidor. Vuelve a intentar;
          si se repite, avisa al equipo con el texto de abajo.
        </p>
        <p className="n small text-body-secondary">{String(this.state.error)}</p>
        <Button variant="danger" onClick={() => this.setState({ error: null })}>Reintentar</Button>
      </Alert>
    )
  }
}

/* Cada pantalla es su propio trozo de JS (React.lazy): la web arranca con el armazón y baja
   solo la pantalla que se abre. Las páginas exportan con nombre; lazy pide `default`. */
const Administracion = lazy(() => import('./paginas/Administracion').then((m) => ({ default: m.Administracion })))
const Catalogo = lazy(() => import('./paginas/Catalogo').then((m) => ({ default: m.Catalogo })))
const Compras = lazy(() => import('./paginas/Compras').then((m) => ({ default: m.Compras })))
const Importar = lazy(() => import('./paginas/Importar').then((m) => ({ default: m.Importar })))
const Movimientos = lazy(() => import('./paginas/Movimientos').then((m) => ({ default: m.Movimientos })))
const Semana = lazy(() => import('./paginas/Semana').then((m) => ({ default: m.Semana })))
const Solicitudes = lazy(() => import('./paginas/Solicitudes').then((m) => ({ default: m.Solicitudes })))
const ManoDeObra = lazy(() => import('./paginas/ManoDeObra').then((m) => ({ default: m.ManoDeObra })))
const Requerimiento = lazy(() => import('./paginas/Requerimiento').then((m) => ({ default: m.Requerimiento })))
const Obras = lazy(() => import('./paginas/Obra').then((m) => ({ default: m.Obras })))
const Parametros = lazy(() => import('./paginas/Parametros').then((m) => ({ default: m.Parametros })))
const Proveedores = lazy(() => import('./paginas/Proveedores').then((m) => ({ default: m.Proveedores })))
const Reposicion = lazy(() => import('./paginas/Reposicion').then((m) => ({ default: m.Reposicion })))
const Inventario = lazy(() => import('./paginas/Inventario').then((m) => ({ default: m.Inventario })))
const Tablero = lazy(() => import('./paginas/Tablero').then((m) => ({ default: m.Tablero })))

// mientras se descarga el código de la pantalla (React.lazy), su esqueleto con Placeholder de Bootstrap
const CargandoPantalla = () => <EsqueletoPagina />

/* Íconos del menú: trazo simple, heredan el color del texto. */
const Icono = ({ d, tam = 16 }: { d: string; tam?: number }) => (
  <svg width={tam} height={tam} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ flex: 'none' }}>
    {d.split('|').map((seg, i) => <path key={i} d={seg} />)}
  </svg>
)
const ICONOS = {
  tablero: 'M3 3v18h18|M7 15v-4|M12 15V7|M17 15v-7',
  reposicion: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9|M10.3 21a1.94 1.94 0 0 0 3.4 0',
  compras: 'M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z|M3 6h18|M16 10a4 4 0 0 1-8 0',
  proveedores: 'M5 18H3V6h12v12H9|M19 18h2v-5l-3-4h-4v9h1|M7.5 20a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3|M17.5 20a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3',
  inventario: 'M3 7l9-4 9 4-9 4-9-4|M3 7v10l9 4 9-4V7|M12 11v10',
  llave: 'M15.5 7.5a3.5 3.5 0 1 1-4.95 4.95 3.5 3.5 0 0 1 4.95-4.95|M10.5 12.5L3 20|M6 17l2 2|M8 15l2 2',
  solicitudes: 'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2|M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2|M9 12h6|M9 16h4',
  personal: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2|M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8|M22 21v-2a4 4 0 0 0-3-3.87|M16 3.13a4 4 0 0 1 0 7.75',
  movimientos: 'M8 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2h-3|M12 17V7|M8 10l4-4 4 4',
  catalogo: 'M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z|M3.3 7 12 12l8.7-5|M12 22V12',
  importar: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4|M7 8l5-5 5 5|M12 3v12',
  parametros: 'M4 21v-7|M4 10V3|M12 21v-9|M12 8V3|M20 21v-5|M20 12V3|M1 14h6|M9 8h6|M17 16h6',
  obra: 'M3 21h18|M5 21V7l7-4 7 4v14|M9 21v-4h6v4|M9 10h.01|M15 10h.01|M9 14h.01|M15 14h.01',
  administracion: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10|M9 12l2 2 4-4',
  salir: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3|M10 17l5-5-5-5|M15 12H3',
}

/* Ícono de cada área del menú (el de su primera pantalla, salvo donde hay uno más claro) */
const ICONO_GRUPO: Record<string, keyof typeof ICONOS> = {
  'Preparar la obra': 'obra', 'Control de la obra': 'tablero', Abastecimiento: 'compras',
  Almacén: 'inventario', Catálogo: 'catalogo', Administración: 'administracion',
}

const iniciales = (nombre: string) => nombre.trim().split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('')

/* Solo para el administrador de la obra activa (HU-13, HU-14, HU-15). Esconderla es comodidad:
   la API verifica el rol en cada petición y responde 403 si no corresponde. */
const GRUPO_ADMINISTRACION: (typeof GRUPOS)[number] = {
  rotulo: 'Administración',
  items: [{ ruta: '/administracion', nombre: 'Administración', icono: 'administracion' }],
}

/** Al entrar, cada rol cae en lo que más usa (antes todos iban a Proveedores). */
export function inicioDelRol(rol: string | null | undefined): string {
  switch (rol) {
    case 'almacenero': return '/movimientos'
    case 'ssoma': return '/mano-de-obra'
    case 'administrador': case 'residente': case 'gerente': return '/tablero'
    default: return '/tablero'
  }
}

// `permiso`: la acción de api/permisos.ts que hace falta para ver la pantalla (el servidor igual lo verifica)
const GRUPOS: { rotulo: string; items: { ruta: string; nombre: string; icono: keyof typeof ICONOS; permiso?: string }[] }[] = [
  {
    // la importación del S10 es parte de PREPARAR la obra (se hace al crearla);
    // aquí vivirán también «Crear la obra» (HU-01) y sus parámetros
    rotulo: 'Preparar la obra',
    items: [
      { ruta: '/obra', nombre: 'Crear la obra', icono: 'obra', permiso: 'proyecto.crear' },
      { ruta: '/importar', nombre: 'Importar del S10', icono: 'importar', permiso: 'requerimiento.importar' },
      { ruta: '/requerimiento', nombre: 'Requerimiento', icono: 'importar', permiso: 'requerimiento.consultar' },
    ],
  },
  {
    rotulo: 'Control de la obra',
    items: [
      // «gasto en recursos», nunca «costo de obra»: el sistema no informa el
      // costo total (gastos generales, subcontratos y planilla quedan fuera)
      { ruta: '/tablero', nombre: 'Gasto en recursos', icono: 'tablero', permiso: 'costo.consultar' },
      { ruta: '/reposicion', nombre: 'Reposición', icono: 'reposicion', permiso: 'reposicion.consultar' },
      { ruta: '/parametros', nombre: 'Reposición: parámetros', icono: 'parametros', permiso: 'reposicion.consultar' },
      { ruta: '/mano-de-obra', nombre: 'Mano de obra', icono: 'personal', permiso: 'trabajador.consultar' },
    ],
  },
  {
    rotulo: 'Abastecimiento',
    items: [
      { ruta: '/solicitudes', nombre: 'Solicitudes de compra', icono: 'solicitudes', permiso: 'solicitud.consultar' },
      { ruta: '/compras', nombre: 'Compras', icono: 'compras', permiso: 'compra.consultar' },
      { ruta: '/proveedores', nombre: 'Proveedores', icono: 'proveedores', permiso: 'proveedor.consultar' },
    ],
  },
  {
    rotulo: 'Almacén',
    items: [
      { ruta: '/movimientos', nombre: 'Ingresos y salidas', icono: 'movimientos', permiso: 'stock.consultar' },
      { ruta: '/semana', nombre: 'Movimiento semanal', icono: 'movimientos', permiso: 'stock.consultar' },
      { ruta: '/inventario', nombre: 'Inventario valorizado', icono: 'inventario', permiso: 'costo.consultar' },
    ],
  },
  {
    rotulo: 'Catálogo',
    items: [
      { ruta: '/catalogo', nombre: 'Catálogo de insumos', icono: 'catalogo' },
    ],
  },
]

/* Ajustes de pantalla (menú «Pantalla» de la barra superior): tema claro u oscuro y tamaño del texto.
   Bootstrap trae el modo oscuro con data-bs-theme en <html>; y como Bootstrap mide todo en rem,
   el tamaño del texto es el font-size de <html>. Se guardan en este dispositivo. */
const ESCALAS = ['90', '100', '110', '125']

function AjustesPantalla() {
  const [tema, setTema] = useState(() => {
    try { return localStorage.getItem('sigro-tema') ?? 'oscuro' } catch { return 'oscuro' }
  })
  const [escala, setEscala] = useState(() => {
    try { return localStorage.getItem('sigro-escala') ?? '100' } catch { return '100' }
  })
  useEffect(() => {
    document.documentElement.dataset.bsTheme = tema === 'oscuro' ? 'dark' : 'light'
    document.documentElement.style.fontSize = `${escala}%`
    try {
      localStorage.setItem('sigro-tema', tema)
      localStorage.setItem('sigro-escala', escala)
    } catch { /* sin almacenamiento, los ajustes no persisten */ }
  }, [tema, escala])
  const instalar = useInstalar()

  return (
    <NavDropdown title="Pantalla" align="end" id="ajustes-pantalla">
      {instalar && (
        <>
          {/* PWA: aparece solo si el navegador permite instalar SIGRO como aplicación */}
          <NavDropdown.Item onClick={instalar}>⬇ Instalar SIGRO como app</NavDropdown.Item>
          <NavDropdown.Divider />
        </>
      )}
      <NavDropdown.Header>Tema</NavDropdown.Header>
      <NavDropdown.Item active={tema === 'claro'} onClick={() => setTema('claro')}>Claro</NavDropdown.Item>
      <NavDropdown.Item active={tema === 'oscuro'} onClick={() => setTema('oscuro')}>Oscuro</NavDropdown.Item>
      <NavDropdown.Divider />
      <NavDropdown.Header>Tamaño del texto</NavDropdown.Header>
      {ESCALAS.map((e) => (
        <NavDropdown.Item key={e} active={escala === e} onClick={() => setEscala(e)}>{e} %</NavDropdown.Item>
      ))}
    </NavDropdown>
  )
}

/* Menú lateral en acordeón (Accordion de Bootstrap): un área abierta a la vez y, al entrar,
   abierta la de la pantalla actual. Cada opción es un Nav.Link que usa el NavLink del router,
   así Bootstrap pinta como «active» la pantalla abierta. */
function MenuSecciones({ grupos, alNavegar }: { grupos: typeof GRUPOS; alNavegar?: () => void }) {
  const { pathname } = useLocation()
  const actual = grupoDeRuta(grupos, pathname)
  const [abierto, setAbierto] = useState<string | null>(actual)
  // al llegar a una pantalla de otra área (por un enlace o con «atrás»), se abre esa área
  useEffect(() => { if (actual) setAbierto(actual) }, [actual])

  return (
    <Accordion as="nav" aria-label="Secciones" activeKey={abierto} onSelect={(k) => setAbierto(typeof k === 'string' ? k : null)}>
      {grupos.map((g) => {
        const icono = ICONOS[ICONO_GRUPO[g.rotulo] ?? g.items[0].icono]
        // un área de una sola pantalla no se despliega: es un enlace directo
        if (g.items.length === 1) {
          const s = g.items[0]
          return (
            <NavLink key={g.rotulo} to={s.ruta} onClick={alNavegar} className="sigro-item">
              <Icono d={icono} tam={20} />{s.nombre}
            </NavLink>
          )
        }
        return (
          <Accordion.Item key={g.rotulo} eventKey={g.rotulo}>
            <Accordion.Header><Icono d={icono} tam={20} />{g.rotulo}</Accordion.Header>
            <Accordion.Body>
              {/* subpáginas colgando de una línea curva; NavLink marca «active» la pantalla abierta */}
              <div className="sigro-sub">
                {g.items.map((s) => (
                  <NavLink key={s.ruta} to={s.ruta} onClick={alNavegar}>{s.nombre}</NavLink>
                ))}
              </div>
            </Accordion.Body>
          </Accordion.Item>
        )
      })}
    </Accordion>
  )
}

/** HT-04: con la API real primero se entra; con el cliente simulado no hace falta.
    Arranque bloquea la web hasta que el servidor responda (Render duerme los servicios). */
export default function App() {
  return (
    <Arranque>
      <Entrada />
    </Arranque>
  )
}

/** Al entrar: la tarjeta de Ingresar se desvanece; si hay varias obras, se elige una; luego «Preparando tu obra»
    hasta que la obra activa y el panel tengan sus datos, y recién entonces aparece el armazón. */
function Entrada() {
  const [sesion, setSesion] = useState(() => sesionActual())
  const previa = useRef(sesion)
  const [saliendo, setSaliendo] = useState(false)
  const [paso, setPaso] = useState<'elegir' | 'preparando' | null>(null)
  const [nombreObra, setNombreObra] = useState('')
  useEffect(() => {
    let espera: ReturnType<typeof setTimeout> | undefined
    const cambio = () => {
      const nueva = sesionActual()
      const recienEntro = !previa.current && nueva !== null
      previa.current = nueva
      if (!recienEntro) { setSesion(nueva); if (!nueva) setPaso(null); return }
      const seguir = () => {
        window.location.hash = '#/tablero' // al entrar se llega al panel de gasto en recursos
        if (nueva.obras.length > 1) setPaso('elegir')
        else { setNombreObra(nueva.obras[0]?.nombre ?? ''); setPaso('preparando') }
        setSaliendo(false)
        setSesion(nueva)
      }
      setSaliendo(true); espera = setTimeout(seguir, 150)
    }
    window.addEventListener('sigro-sesion', cambio)
    return () => { window.removeEventListener('sigro-sesion', cambio); clearTimeout(espera) }
  }, [])
  const terminar = useCallback(() => setPaso(null), [])

  if (usaApiReal && !sesion) return <Entrar saliendo={saliendo} />
  if (paso === 'elegir' && sesion) {
    return (
      <ElegirObra
        nombre={sesion.usuario.nombre.trim().split(/\s+/)[0]}
        obras={sesion.obras.map((o) => ({ id: String(o.id), nombre: o.nombre, rol: ROLES[o.rol] ?? o.rol }))}
        alElegir={async (o) => { await api.elegirObra(o.id); setNombreObra(o.nombre); setPaso('preparando') }}
      />
    )
  }
  return <Sistema preparando={paso === 'preparando'} nombreObra={nombreObra} alTerminar={terminar} />
}

const SIN_PANEL_MS = 15000 // si el panel no avisa, la entrada no se queda esperando

function Sistema({ preparando = false, nombreObra = '', alTerminar }: {
  preparando?: boolean
  nombreObra?: string
  alTerminar?: () => void
}) {
  const [obra, setObra] = useState<ObraActiva | null>(null)
  const [obraFallo, setObraFallo] = useState(false)
  const [obras, setObras] = useState<Obra[]>([])
  useEffect(() => {
    api.obraActiva().then(setObra, () => setObraFallo(true))
    api.listarObras().then(setObras)
    // la pantalla «Crear la obra» avisa cuando hay una nueva, para que el
    // selector de obra activa la ofrezca sin recargar
    const refrescar = () => api.listarObras().then(setObras)
    window.addEventListener('sigro-obras', refrescar)
    return () => window.removeEventListener('sigro-obras', refrescar)
  }, [])

  // entrada a la obra: el plano termina de dibujarse, y la obra activa y el panel ya cargaron
  const [dibujado, setDibujado] = useState(false)
  const [panelListo, setPanelListo] = useState(false)
  const [mostrando, setMostrando] = useState(false)
  useEffect(() => {
    if (!preparando) return
    const listo = () => setPanelListo(true)
    window.addEventListener('sigro-panel-listo', listo)
    const tope = setTimeout(listo, SIN_PANEL_MS)
    return () => { window.removeEventListener('sigro-panel-listo', listo); clearTimeout(tope) }
  }, [preparando])
  const todoListo = preparando && dibujado && panelListo && (obra !== null || obraFallo)
  useEffect(() => {
    if (!todoListo) return
    setMostrando(true)                                   // la capa sale y el armazón funde (350 ms)
    const t = setTimeout(() => alTerminar?.(), 350)
    return () => clearTimeout(t)
  }, [todoListo, alTerminar])
  const marco = useRef<HTMLDivElement>(null)
  useInerte(marco, preparando && !mostrando)

  // regla 9: al cambiar de obra, lo de empresa queda y lo de obra cambia.
  // La clave en el contenedor remonta las pantallas para que vuelvan a consultar.
  const cambiarObra = async (id: string) => {
    setObra(await api.elegirObra(id))
    setObras(await api.listarObras())
  }

  const sesion = sesionActual()
  const rol = sesion?.obras.find((o) => String(o.id) === obra?.id)?.rol ?? sesion?.obras[0]?.rol
  // cada rol ve solo las pantallas que puede usar; un grupo vacío no se muestra
  const visibles = usaApiReal
    ? GRUPOS.map((g) => ({ ...g, items: g.items.filter((i) => !i.permiso || puede(rol, i.permiso)) })).filter((g) => g.items.length)
    : GRUPOS
  const grupos = usaApiReal && esAdministrador(sesion, obra?.id) ? [...visibles, GRUPO_ADMINISTRACION] : visibles

  const [menuAbierto, setMenuAbierto] = useState(false)
  const [cambiandoClave, setCambiandoClave] = useState(false)

  return (
    <HashRouter>
      {preparando && (
        <CapaPlano
          etiqueta="Preparando tu obra"
          titulo={`Preparando tu obra · ${obra?.nombre ?? nombreObra}`}
          progreso={(dibujado ? 0.34 : 0) + (obra || obraFallo ? 0.33 : 0) + (panelListo ? 0.33 : 0)}
          saliendo={mostrando}
          alDibujarse={() => setDibujado(true)}
        />
      )}
      <div ref={marco} className="sigro-armazon">
        {/* Menú flotante (Offcanvas «responsive»): a partir de lg se ve fijo a la izquierda; en pantallas chicas se abre con ☰ */}
        <Offcanvas show={menuAbierto} onHide={() => setMenuAbierto(false)} responsive="lg" className="sigro-menu" aria-label="Menú">
          <Offcanvas.Body className="d-block p-0">
            <div className="sigro-panel">
              <div className="d-flex align-items-start justify-content-between">
                <NavLink to="/tablero" aria-label="SIGRO · inicio" className="d-block px-2 pt-1" onClick={() => setMenuAbierto(false)}>
                  <img src={logoBlanco} alt="SEVEN'S Ingenieros SAC" height={44} />
                </NavLink>
                <button type="button" className="btn-close btn-close-white d-lg-none" aria-label="Cerrar menú" onClick={() => setMenuAbierto(false)} />
              </div>
              <MenuSecciones grupos={grupos} alNavegar={() => setMenuAbierto(false)} />
              {/* la API decide quién registra compras (compra.registrar); aquí solo se ofrece el atajo */}
              {(!rol || ['almacenero', 'residente', 'administrador'].includes(rol)) && (
                <div className="sigro-llamado">
                  <div className="titulo">¿Llegó una compra?</div>
                  <NavLink to="/compras?nueva=1" className="btn btn-primary w-100" onClick={() => setMenuAbierto(false)}>+ Registrar compra</NavLink>
                </div>
              )}
              {/* perfil abajo, con su botón de salir: el menú entra entero en una laptop (1366×768) sin barra propia */}
              {sesion && (
                <div className="sigro-perfil">
                  <span className="avatar" aria-hidden="true">{iniciales(sesion.usuario.nombre)}</span>
                  <div className="min-w-0 flex-grow-1">
                    <div className="nombre text-truncate">{sesion.usuario.nombre}</div>
                    {rol && <div className="rol">{ROLES[rol] ?? rol}</div>}
                  </div>
                  {usaApiReal && (
                    <button type="button" className="sigro-salir" onClick={() => setCambiandoClave(true)} aria-label="Cambiar mi contraseña" title="Cambiar mi contraseña">
                      <Icono d={ICONOS.llave} tam={19} />
                    </button>
                  )}
                  {usaApiReal && (
                    <button type="button" className="sigro-salir" onClick={cerrarSesion} aria-label="Cerrar sesión" title="Cerrar sesión">
                      <Icono d={ICONOS.salir} tam={20} />
                    </button>
                  )}
                </div>
              )}
            </div>
          </Offcanvas.Body>
        </Offcanvas>

        <div className="sigro-columna">
          {/* Barra superior: obra activa, semana del plazo y ajustes de pantalla */}
          <header className="sigro-barra">
            <Button variant="primary" className="d-lg-none" onClick={() => setMenuAbierto(true)} aria-label="Abrir menú">☰</Button>
            {/* obra activa: si hay más de una, se elige aquí (regla 9: solo ves lo de esta obra) */}
            <div className="sigro-obra d-flex flex-wrap align-items-center gap-2">
              <label htmlFor="obra-activa" className="small text-body-secondary">Obra</label>
              {obras.length > 1 ? (
                <Form.Select id="obra-activa" value={obra?.id ?? ''} onChange={(e) => cambiarObra(e.target.value)}>
                  {obras.map((o) => <option key={o.id} value={o.id}>{o.nombre}</option>)}
                </Form.Select>
              ) : (
                <span id="obra-activa" className="fw-semibold">{obra?.nombre ?? '…'}</span>
              )}
              {obra && <Badge pill>{obra.semana === 0 ? 'aún no inicia' : `semana ${obra.semana} de ${obra.semanasTotales}`}</Badge>}
            </div>
            <Nav className="ms-auto align-items-center">
              {usaApiReal && sesion && <Campana />}
              <AjustesPantalla />
            </Nav>
          </header>

          <main className="flex-grow-1 p-3 p-lg-4 min-w-0">
            {/* la clave remonta las pantallas al cambiar de obra: vuelven a consultar */}
            <Container fluid key={obra?.id ?? 'sin-obra'} className="px-0">
              <GuardianErrores>
                <Suspense fallback={<CargandoPantalla />}>
                  <Routes>
                    <Route path="/" element={<Navigate to={inicioDelRol(rol)} replace />} />
                    <Route path="/proveedores" element={<Proveedores />} />
                    <Route path="/compras" element={<Compras />} />
                    <Route path="/tablero" element={<Tablero />} />
                    <Route path="/inventario" element={<Inventario />} />
                    <Route path="/reposicion" element={<Reposicion />} />
                    <Route path="/catalogo" element={<Catalogo />} />
                    <Route path="/movimientos" element={<Movimientos />} />
                    <Route path="/semana" element={<Semana />} />
                    <Route path="/mano-de-obra" element={<ManoDeObra />} />
                    <Route path="/solicitudes" element={<Solicitudes />} />
                    <Route path="/requerimiento" element={<Requerimiento />} />
                    <Route path="/importar" element={<Importar />} />
                    <Route path="/obra" element={<Obras />} />
                    <Route path="/parametros" element={<Parametros />} />
                    <Route path="/administracion" element={<Administracion obra={obra} />} />
                    <Route path="/administracion/:seccion" element={<Administracion obra={obra} />} />
                  </Routes>
                </Suspense>
              </GuardianErrores>
            </Container>
          </main>
        </div>
      </div>
      {cambiandoClave && <CambiarClave enCerrar={() => setCambiandoClave(false)} />}
    </HashRouter>
  )
}
