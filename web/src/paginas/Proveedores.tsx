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
   - El alta exige RUC (11 dígitos) o DNI (8, decisión D-C5) y razón social; el
     documento repetido se rechaza. El DNI queda «sin validar»: no va a SUNAT.
   El tercer criterio de HU-30 («al guardarlo queda seleccionado en la compra en
   curso») se demuestra en la pantalla de compra, que reutiliza este mismo alta.
   Estilo vidrio: sin lienzo propio, con el lenguaje de W6 «Compras».
   ───────────────────────────────────────────────────────────────────────── */
import { usaApiReal } from '../api/http'
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import { consultarRucEnSunat, type RucSunat } from '../api/sunat'
import type { EstadoProveedor, Proveedor } from '../api/tipos'
import { Campo, Chip, Modal, Vacio, type TonoChip } from '../ui/base'
import { Chips, Encabezado, Fondo, Pildora, Vidrio } from '../ui/vidrio'

const ESTADO: Record<EstadoProveedor, { texto: string; tono: TonoChip }> = {
  validado: { texto: 'Validado en SUNAT', tono: 'bien' },
  sin_validar: { texto: 'Por validar', tono: 'aviso' },
  con_alerta_sunat: { texto: 'Con alerta SUNAT', tono: 'aviso' },
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
          etiqueta="RUC o DNI"
          valor={ruc}
          enCambio={alCambiarRuc}
          marcador="RUC de 11 dígitos o DNI de 8"
          autoFoco
          error={error?.campo === 'ruc' ? error.texto : undefined}
        />
        {consultando && (
          <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>Consultando en SUNAT…</span>
        )}
        {sunat && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <Chip tono={sunat.apto_para_comprar ? 'bien' : 'aviso'}>
              SUNAT: {sunat.estado_sunat} · {sunat.condicion}
            </Chip>
            <span style={{ fontSize: 12, color: 'var(--tinta-2)' }}>razón social traída del padrón</span>
          </div>
        )}
        <Campo
          id="alta-razon"
          etiqueta="Razón social"
          valor={razon}
          enCambio={setRazon}
          error={error?.campo === 'razon' ? error.texto : undefined}
        />
        <p style={{ fontSize: 12.5, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
          Con los 11 dígitos del RUC, la razón social se trae sola del padrón de
          SUNAT (vía el servicio de integraciones). Si el servicio no responde,
          se escribe a mano y el proveedor queda «sin validar»: la compra no se
          bloquea.
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, justifyContent: 'flex-end' }}>
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={guardar} deshabilitado={guardando}>
            {guardando ? 'Guardando…' : 'Guardar proveedor'}
          </Pildora>
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
    <>
    <Fondo dibujo="proveedores" />
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Encabezado
        titulo="Proveedores"
        sub="RUC único en todo el sistema · un proveedor de baja no aparece al registrar compras nuevas"
        acciones={<Pildora enClic={() => setAltaAbierta(true)}>Registrar proveedor</Pildora>}
      />

      <Chips>
        <Chip tono="marca">Proveedores reales del cliente{todos ? ` · ${todos.length.toLocaleString('es-PE')}` : ''}</Chip>
        {usaApiReal
          ? <Chip tono="info">Compras y última compra: del servidor · días de entrega: aún sin registrar</Chip>
          : <Chip tono="info">Compras, entrega y última compra: datos de ejemplo</Chip>}
      </Chips>

      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Lista de proveedores">
        <label className="vidrio-fuerte" style={{ height: 52, borderRadius: 26, padding: '0 18px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accion)" strokeWidth="2.4" strokeLinecap="round" style={{ flex: 'none' }}>
            <circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" />
          </svg>
          <input
            value={busqueda}
            onChange={(e) => { setBusqueda(e.target.value); setTope(60) }}
            placeholder="Buscar por razón social o RUC…"
            aria-label="Buscar proveedor por razón social o RUC"
            className="foco-vidrio"
            style={{ flex: 1, minWidth: 0, height: 40, border: 0, background: 'transparent', fontSize: 15, fontFamily: 'inherit', color: 'var(--tinta)' }}
          />
        </label>

        {visibles === null ? (
          <Vacio titulo="Cargando…" detalle="Consultando los proveedores de la empresa." />
        ) : visibles.length === 0 ? (
          <Vacio
            titulo="Ningún proveedor coincide"
            detalle="Prueba con otra razón social u otro RUC, o registra el proveedor."
          />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="tabla-vidrio">
              <thead>
                <tr>
                  <th>Proveedor</th><th>RUC</th><th>Estado</th>
                  <th className="n">Entrega</th><th className="n">Compras</th><th className="n">Última compra</th>
                </tr>
              </thead>
              <tbody>
                {recorte!.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 600, minWidth: 260 }}>{p.razonSocial}</td>
                    <td className="n" style={{ textAlign: 'left', color: 'var(--tinta-2)' }}>{p.ruc}</td>
                    <td><Chip tono={ESTADO[p.estado].tono}>{ESTADO[p.estado].texto}</Chip></td>
                    <td className="n" style={{ whiteSpace: 'nowrap' }}>{p.diasEntrega == null ? '—' : `${p.diasEntrega} días`}</td>
                    <td className="n">{p.comprasRegistradas}</td>
                    <td className="n" style={{ whiteSpace: 'nowrap' }}>{p.ultimaCompra ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {visibles && visibles.length > tope && (
          <div style={{ paddingTop: 6, display: 'flex', justifyContent: 'center' }}>
            <Pildora tipo="secundario" enClic={() => setTope(tope + 60)}>
              Mostrar 60 más · van {tope} de {visibles.length.toLocaleString('es-PE')}
            </Pildora>
          </div>
        )}
      </Vidrio>

      <p style={{ fontSize: 13, color: 'var(--tinta-plano)', padding: '0 4px', lineHeight: 1.5 }}>
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
    </>
  )
}
