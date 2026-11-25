/* SIGRO · Administración · quién puede qué. Es la MISMA matriz que aplica el servidor (api/permisos.ts es copia
   exacta de api/app/permisos.py y una prueba lo vigila). Solo lectura: los permisos se cambian en el código, con PR. */
import { Fragment } from 'react'
import { DESCRIPCION, GRUPOS_PERMISOS, ROLES, puede, type Rol } from '../../api/permisos'

const NOMBRE: Record<Rol, string> = { almacenero: 'Almacenero', ssoma: 'SSOMA', residente: 'Residente', administrador: 'Administrador', gerente: 'Gerente' }

export function Permisos({ miRol }: { miRol: string | null }) {
  return (
    <div className="d-flex flex-column gap-3">
      <p className="small text-body-secondary mb-0">
        Cada permiso vale solo en las obras donde la persona está asignada, y lo verifica el servidor en cada pedido.
        Para cambiarlo se modifica <code>permisos.py</code> con su Pull Request.
      </p>
      <div className="overflow-x-auto">
        <table className="table table-sm align-middle mb-0 matriz-permisos" aria-label="Matriz de permisos por rol">
          <thead>
            <tr>
              <th scope="col">Acción</th>
              {ROLES.map((r) => <th key={r} scope="col" className={`text-center ${r === miRol ? 'mi-rol' : ''}`}>{NOMBRE[r]}{r === miRol && <div className="small fw-normal">(tú)</div>}</th>)}
            </tr>
          </thead>
          <tbody>
            {GRUPOS_PERMISOS.map((g) => (
              <Fragment key={g.grupo}>
                <tr className="grupo"><th colSpan={ROLES.length + 1} scope="rowgroup">{g.grupo}</th></tr>
                {g.acciones.map((a) => (
                  <tr key={a}>
                    <th scope="row" className="fw-normal">{DESCRIPCION[a]}<div className="small text-body-secondary"><code>{a}</code></div></th>
                    {ROLES.map((r) => (
                      <td key={r} className={`text-center ${r === miRol ? 'mi-rol' : ''}`}>
                        {puede(r, a)
                          ? <span className="si" role="img" aria-label={`${NOMBRE[r]}: sí`}>✓</span>
                          : <span className="no" role="img" aria-label={`${NOMBRE[r]}: no`}>·</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
