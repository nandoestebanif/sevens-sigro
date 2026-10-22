/* ─────────────────────────────────────────────────────────────────────────────
   HU-29 · Como residente de obra, quiero consultar la lista de proveedores,
           para elegir uno sin volver a escribir sus datos.
   HU-30 · Como residente de obra, quiero crear un proveedor desde el formulario
           de compra, para no interrumpir el registro cuando aún no existe.

   Criterios cubiertos aquí:
   - Se busca por razón social o por RUC.
   - Se muestran razón social, RUC y estado.
   - Un proveedor dado de baja sigue visible aquí (historial), pero el selector
     de la compra usa `proveedoresParaComprar()`, que lo excluye.
   - El alta exige RUC (11 dígitos) y razón social; el RUC repetido se rechaza.
   El tercer criterio de HU-30 («al guardarlo queda seleccionado en la compra en
   curso») se demuestra en la pantalla de compra, que reutiliza este mismo alta.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import { consultarRucEnSunat, type RucSunat } from '../api/sunat'
import type { EstadoProveedor, Proveedor } from '../api/tipos'
import { Boton, Campo, Chip, Modal, Tarjeta, Titulo, Vacio, type TonoChip } from '../ui/base'

const ESTADO: Record<EstadoProveedor, { texto: string; tono: TonoChip }> = {
  validado: { texto: 'Validado en SUNAT', tono: 'bien' },
  sin_validar: { texto: 'Sin validar', tono: 'aviso' },
  de_baja: { texto: 'De baja', tono: 'neutro' },
}

export function AltaProveedor({ enCerrar, enCreado }: {
  enCerrar: () => void
  enCreado: (p: Proveedor) => void
}) {
  const [ruc, setRuc] = useState('')
  const [razon, setRazon] = useState('')
  const [error, setError] = useState<{ campo: string; texto: string } | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [sunat, setSunat] = useState<RucSunat | null>(null)
  const [consultando, setConsultando] = useState(false)

  // HU-31: con los 11 dígitos puestos, se consulta SUNAT (vía el micro de
  // integraciones) y la razón social se llena sola. Si el micro está apagado,
  // no pasa nada: se escribe a mano y queda «sin validar».
  const alCambiarRuc = async (v: string) => {
    setRuc(v)
    setSunat(null)
    if (/^\d{11}$/.test(v.trim())) {
      setConsultando(true)
      const r = await consultarRucEnSunat(v.trim())
      setConsultando(false)
      if (r) {
        setSunat(r)
        setRazon(r.razon_social)
      }
    }
  }

  const guardar = async () => {
    setGuardando(true)
    setError(null)
    try {
      const creado = await api.crearProveedor({
        ruc, razonSocial: razon, validadoEnSunat: sunat?.apto_para_comprar ?? false,
      })
      enCreado(creado)
    } catch (e) {
      if (e instanceof ErrorApi) {
        const campo = e.codigo === 'razon_social' ? 'razon' : 'ruc'
        setError({ campo, texto: e.message })
      }
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Registrar proveedor" enCerrar={enCerrar}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Campo
          id="alta-ruc"
          etiqueta="RUC"
          valor={ruc}
          enCambio={alCambiarRuc}
          marcador="11 dígitos"
          autoFoco
          error={error?.campo === 'ruc' ? error.texto : undefined}
        />
        {consultando && (
          <span style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>Consultando en SUNAT…</span>
        )}
        {sunat && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <Chip tono={sunat.apto_para_comprar ? 'bien' : 'aviso'}>
              SUNAT: {sunat.estado_sunat} · {sunat.condicion}
            </Chip>
            <span style={{ fontSize: 12, color: 'var(--tinta-3)' }}>razón social traída del padrón</span>
          </div>
        )}
        <Campo
          id="alta-razon"
          etiqueta="Razón social"
          valor={razon}
          enCambio={setRazon}
          error={error?.campo === 'razon' ? error.texto : undefined}
        />
        <p style={{ fontSize: 12.5, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
          Con los 11 dígitos del RUC, la razón social se trae sola del padrón de
          SUNAT (vía el servicio de integraciones). Si el servicio no responde,
          se escribe a mano y el proveedor queda «sin validar»: la compra no se
          bloquea.
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={guardando}>
            {guardando ? 'Guardando…' : 'Guardar proveedor'}
          </Boton>
        </div>
      </div>
    </Modal>
  )
}

export function Proveedores() {
  const [todos, setTodos] = useState<Proveedor[] | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [altaAbierta, setAltaAbierta] = useState(false)
  const [tope, setTope] = useState(60)

  const cargar = () => api.listarProveedores().then(setTodos)
  useEffect(() => {
    cargar()
  }, [])

  const visibles = useMemo(() => {
    if (!todos) return null
    const q = busqueda.trim().toLowerCase()
    // los que llevan historia de compras van arriba; el resto, alfabético
    const orden = [...todos].sort((a, b) => b.comprasRegistradas - a.comprasRegistradas)
    if (!q) return orden
    return orden.filter((p) => p.razonSocial.toLowerCase().includes(q) || p.ruc.includes(q))
  }, [todos, busqueda])
  const recorte = visibles?.slice(0, tope) ?? null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
        <Titulo sub="RUC único en todo el sistema · un proveedor de baja no aparece al registrar compras nuevas">
          Proveedores
        </Titulo>
        <Boton enClic={() => setAltaAbierta(true)}>Registrar proveedor</Boton>
      </div>

      <input
        value={busqueda}
        onChange={(e) => { setBusqueda(e.target.value); setTope(60) }}
        placeholder="Buscar por razón social o RUC…"
        aria-label="Buscar proveedor por razón social o RUC"
        style={{
          height: 42, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)',
          padding: '0 14px', fontSize: 14, fontFamily: 'inherit', background: 'var(--tarjeta)',
          color: 'var(--tinta)', outlineColor: 'var(--accion)',
        }}
      />

      <Tarjeta relleno={false}>
        {visibles === null ? (
          <Vacio titulo="Cargando…" detalle="Consultando los proveedores de la empresa." />
        ) : visibles.length === 0 ? (
          <Vacio
            titulo="Ningún proveedor coincide"
            detalle="Prueba con otra razón social u otro RUC, o registra el proveedor."
          />
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                {['Proveedor', 'RUC', 'Estado', 'Entrega', 'Compras', 'Última compra'].map((h, i) => (
                  <th
                    key={h}
                    style={{
                      padding: '11px 18px', fontSize: 11, fontWeight: 600,
                      color: 'var(--tinta-3)', letterSpacing: 0.4,
                      textAlign: i >= 3 ? 'right' : 'left',
                    }}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {recorte!.map((p, i) => (
                <tr key={p.id} style={{ borderBottom: i === recorte!.length - 1 ? 'none' : '1px solid var(--linea)' }}>
                  <td style={{ padding: '12px 18px', fontSize: 13.5, fontWeight: 600 }}>{p.razonSocial}</td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)' }}>{p.ruc}</td>
                  <td style={{ padding: '12px 18px' }}>
                    <Chip tono={ESTADO[p.estado].tono}>{ESTADO[p.estado].texto}</Chip>
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {p.diasEntrega == null ? '—' : `${p.diasEntrega} días`}
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {p.comprasRegistradas}
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {p.ultimaCompra ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {visibles && visibles.length > tope && (
          <div style={{ padding: 14, display: 'flex', justifyContent: 'center', borderTop: '1px solid var(--borde)' }}>
            <Boton tipo="secundario" enClic={() => setTope(tope + 60)}>
              Mostrar 60 más · van {tope} de {visibles.length.toLocaleString('es-PE')}
            </Boton>
          </div>
        )}
      </Tarjeta>

      <p style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
        El plazo de entrega de cada proveedor alimenta el punto de reposición. Los días se
        recalculan con las recepciones reales.
      </p>

      {altaAbierta && (
        <AltaProveedor
          enCerrar={() => setAltaAbierta(false)}
          enCreado={() => {
            setAltaAbierta(false)
            cargar()
          }}
        />
      )}
    </div>
  )
}
