/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · Administración de la obra activa
   HU-15 celulares · HU-13 usuarios · HU-14 asignaciones · almacenes.

   Solo el administrador de la obra activa ve esta sección en el menú. Es
   comodidad: cada petición la verifica la API (regla 9) y, si no corresponde,
   responde 403 y aquí se dice en lenguaje claro.
   Sin API real (VITE_API_URL) no hay nada que administrar: no se inventan datos.
   ───────────────────────────────────────────────────────────────────────── */
import { useParams } from 'react-router-dom'
import { sesionActual, usaApiReal } from '../api/http'
import { Tarjeta, Titulo, Vacio } from '../ui/base'
import { Almacenes } from './administracion/Almacenes'
import { Asignaciones } from './administracion/Asignaciones'
import { Celulares } from './administracion/Celulares'
import { Usuarios } from './administracion/Usuarios'
import { Permisos } from './administracion/Permisos'
import { rolActual } from '../api/permisos'
import { esAdministrador } from './administracion/reglas'
import { EsqueletoTabla } from '../ui/Esqueleto'

const PESTANAS = [
  { clave: 'celulares', texto: 'Celulares' },
  { clave: 'usuarios', texto: 'Usuarios' },
  { clave: 'asignaciones', texto: 'Asignaciones' },
  { clave: 'almacenes', texto: 'Almacenes' },
  { clave: 'permisos', texto: 'Permisos' },
] as const
type Pestana = (typeof PESTANAS)[number]['clave']
const SUBTITULO: Record<Pestana, string> = {
  celulares: 'Autoriza o revoca el celular del almacenero y del SSOMA.',
  usuarios: 'Crea usuarios, corrige sus datos y dalos de baja.',
  asignaciones: 'Quién trabaja en esta obra y con qué rol.',
  almacenes: 'Los almacenes y frentes de la obra.',
  permisos: 'Qué puede hacer cada rol.',
}

export function Administracion({ obra }: { obra: { id: string; nombre: string } | null }) {
  const { seccion } = useParams()
  const activa: Pestana = PESTANAS.some((p) => p.clave === seccion) ? (seccion as Pestana) : 'celulares'
  const sesion = sesionActual()

  if (!usaApiReal) {
    return (
      <div className="d-flex flex-column gap-3 pb-4"><Tarjeta>
        <Vacio titulo="La administración necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: aquí no se muestran datos de ejemplo." />
      </Tarjeta></div>
    )
  }
  if (!obra) return <div className="d-flex flex-column gap-3 pb-4" role="status"><EsqueletoTabla /></div>
  if (!esAdministrador(sesion, obra.id)) {
    return (
      <div className="d-flex flex-column gap-3 pb-4"><Tarjeta>
        <Vacio titulo="Solo para el administrador" detalle={`No eres administrador de ${obra.nombre}. Si deberías serlo, pídeselo al administrador de la obra.`} />
      </Tarjeta></div>
    )
  }

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      {/* cada sección es su propio módulo del menú lateral (Administración → Celulares, Usuarios…) */}
      <Titulo sub={`${obra.nombre} · ${SUBTITULO[activa]} Cada cambio queda con tu nombre y la fecha; nada se borra.`}>
        {PESTANAS.find((p) => p.clave === activa)!.texto}
      </Titulo>
      <div id="panel-administracion">
        {activa === 'celulares' && <Celulares obraId={obra.id} />}
        {activa === 'usuarios' && <Usuarios usuarioSesionId={sesion?.usuario.id ?? null} />}
        {activa === 'asignaciones' && <Asignaciones obraId={obra.id} obraNombre={obra.nombre} />}
        {activa === 'almacenes' && <Almacenes obraId={obra.id} obraNombre={obra.nombre} />}
        {activa === 'permisos' && <Permisos miRol={rolActual(obra.id)} />}
      </div>
    </div>
  )
}
