/* ─────────────────────────────────────────────────────────────────────────────
   HU-13 · Como administrador, quiero asignar a cada persona un usuario y un
           rol, para limitar lo que cada quien ve y registra.
   HU-14 · Como administrador, quiero asignar usuarios a los proyectos, para que
           cada quien opere solo en sus obras.
   HU-15 · Como administrador, quiero autorizar el dispositivo que registra
           movimientos en cada obra, para impedir que un equipo ajeno opere en
           nombre de la obra.
   Lienzo W8 «Usuarios y celulares» (estilo vidrio).

   VISTA DE EJEMPLO. El servicio obras ya expone usuarios, asignaciones y
   dispositivos (/usuarios, /obras/{id}/asignaciones, /obras/{id}/dispositivos),
   pero la capa de API de la web todavía no los consume. Hasta conectarla, esta
   página muestra el diseño con marcadores y sus acciones quedan deshabilitadas:
   no simula autorizar ni revocar, porque el control de acceso lo decide el
   servidor (regla 9) y un botón que «funciona» sin él engañaría.
   ───────────────────────────────────────────────────────────────────────── */
import { Chip, type TonoChip } from '../ui/base'
import { Chips, Encabezado, Fondo, Pildora, TituloSeccion, Vidrio } from '../ui/vidrio'

const USUARIOS: { rol: string; tono: TonoChip }[] = [
  { rol: 'Residente de obra', tono: 'marca' },
  { rol: 'Almacenero', tono: 'info' },
  { rol: 'Ingeniero SSOMA', tono: 'info' },
  { rol: 'Administrador', tono: 'neutro' },
  { rol: 'Gerente', tono: 'neutro' },
]

const CELULARES: { responsable: string; codigo: string; estado: string; tono: TonoChip }[] = [
  { responsable: 'Almacenero', codigo: 'sigro-android-[código]', estado: 'Activo', tono: 'bien' },
  { responsable: 'Ingeniero SSOMA', codigo: 'sigro-ios-[código]', estado: 'Pendiente', tono: 'aviso' },
]

const PENDIENTE = 'Pendiente de conectar con el servicio obras'

export function Accesos() {
  return (
    <>
      <Fondo dibujo="accesos" />
      <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Encabezado
          titulo="Usuarios y celulares"
          sub="Quién entra, con qué rol, en qué obra y desde qué celular"
          acciones={<Pildora deshabilitado etiqueta={`Agregar usuario · ${PENDIENTE}`}>+ Agregar usuario</Pildora>}
        />

        <Chips>
          <Chip tono="info">Vista de ejemplo: nombres y códigos son [marcadores]</Chip>
          <Chip tono="neutro">{PENDIENTE}</Chip>
        </Chips>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-start' }}>
          <Vidrio estilo={{ flex: '999 1 560px', display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Asignados a la obra">
            <TituloSeccion>Asignados a la obra activa</TituloSeccion>
            <div style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio" style={{ minWidth: 640 }}>
                <thead>
                  <tr><th>Usuario</th><th>Nombre</th><th>Rol en esta obra</th><th>Estado</th><th>Asignado</th><th aria-label="Acción" /></tr>
                </thead>
                <tbody>
                  {USUARIOS.map((u) => (
                    <tr key={u.rol}>
                      <td style={{ fontWeight: 700 }}>[usuario]</td>
                      <td>[Nombre y apellido]</td>
                      <td><Chip tono={u.tono}>{u.rol}</Chip></td>
                      <td><Chip tono="bien">Activo</Chip></td>
                      <td style={{ color: 'var(--tinta-2)', whiteSpace: 'nowrap' }}>desde [fecha]</td>
                      <td style={{ textAlign: 'right' }}>
                        <Pildora tipo="secundario" chica deshabilitado etiqueta={`Cambiar el rol de ${u.rol} · ${PENDIENTE}`}>Cambiar</Pildora>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <span style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
              El rol vale solo en esta obra y lo verifica el servidor en cada pedido. Quien deja la obra se retira con su fecha: no se borra.
            </span>
          </Vidrio>

          <aside className="vidrio-fuerte" aria-label="Celulares autorizados" style={{ flex: '1 1 320px', minWidth: 0, borderRadius: 'var(--radio-vidrio)', padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 12, boxShadow: 'var(--vidrio-sombra-alta)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
              <TituloSeccion>Celulares autorizados</TituloSeccion>
              <span className="n" style={{ fontSize: 14, fontWeight: 800 }}>1 de 2</span>
            </div>
            {CELULARES.map((c) => (
              <div key={c.responsable} style={{ background: 'var(--vidrio-celda)', borderRadius: 16, padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <span style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 700 }}>
                  {c.responsable}<Chip tono={c.tono}>{c.estado}</Chip>
                </span>
                <span style={{ fontFamily: 'ui-monospace, monospace', fontSize: 13, color: 'var(--tinta-2)', wordBreak: 'break-all' }}>{c.codigo}</span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {c.estado === 'Pendiente' ? (
                    <>
                      <Pildora chica deshabilitado etiqueta={`Autorizar el celular del ${c.responsable} · ${PENDIENTE}`}>Autorizar</Pildora>
                      <Pildora tipo="secundario" chica deshabilitado etiqueta={`Rechazar el celular del ${c.responsable} · ${PENDIENTE}`}>Rechazar</Pildora>
                    </>
                  ) : (
                    <Pildora tipo="secundario" chica deshabilitado etiqueta={`Revocar el celular del ${c.responsable} · ${PENDIENTE}`}>Revocar con motivo</Pildora>
                  )}
                </div>
              </div>
            ))}
            <span style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.45 }}>
              Máximo dos por obra: el del almacenero y el del ingeniero SSOMA. Un registro que llegue desde otro celular lo rechaza el servidor.
            </span>
          </aside>
        </div>
      </div>
    </>
  )
}
