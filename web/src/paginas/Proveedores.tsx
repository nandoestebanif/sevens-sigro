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
import { consultarRucEnSunat, rucValido, type RucSunat } from '../api/sunat'
import { proveedores as proveedoresOficina } from '../api/oficina'
import type { EstadoProveedor, Proveedor } from '../api/tipos'
import { Chip, Modal, type TonoChip } from '../ui/base'
import { Aviso, Chips, Encabezado, Pildora, Segmentado, Vidrio } from '../ui/piezas'
import { AccionesFila, TablaDatos } from '../ui/TablaDatos'
import { puedo } from '../api/permisos'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Form } from 'react-bootstrap'
import { esquemaProveedor, type DatosProveedor } from '../formularios/esquemas'

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
  // React Hook Form + Zod: el esquema (formularios/esquemas.ts) valida; el formulario guarda los valores y errores.
  const { register, handleSubmit, setValue, setError, watch, formState: { errors, isSubmitting } } = useForm<DatosProveedor>({
    resolver: zodResolver(esquemaProveedor),
    defaultValues: { ruc: '', razonSocial: '' },
  })
  const [sunat, setSunat] = useState<RucSunat | null>(null)
  const [consultando, setConsultando] = useState(false)
  const [avisoRuc, setAvisoRuc] = useState('')
  const ruc = watch('ruc')

  // HU-31: con los 11 dígitos puestos y dígito verificador válido, se consulta SUNAT
  // y la razón social se llena sola. Si el micro está apagado, queda «sin validar».
  useEffect(() => {
    setSunat(null); setAvisoRuc('')
    const limpio = ruc.trim()
    if (!/^\d{11}$/.test(limpio)) return
    if (!rucValido(limpio)) { setAvisoRuc('El dígito verificador de este RUC no coincide con la fórmula SUNAT.'); return }
    let vigente = true
    setConsultando(true)
    consultarRucEnSunat(limpio).then((r) => {
      if (!vigente) return
      setConsultando(false)
      if (r) { setSunat(r); setValue('razonSocial', r.razon_social ?? '', { shouldValidate: true }) }
    })
    return () => { vigente = false }
  }, [ruc, setValue])

  // handleSubmit solo llama a esto si el esquema pasó: aquí los datos ya están validados
  const guardar = async (d: DatosProveedor) => {
    try {
      const creado = await api.crearProveedor({ ruc: d.ruc, razonSocial: d.razonSocial, validadoEnSunat: sunat?.apto_para_comprar ?? false })
      enCreado(creado)
    } catch (e) {
      if (e instanceof ErrorApi) {
        // Un 422 de validación trae el texto de Pydantic («String should…»): nunca se muestra tal cual.
        const tecnico = /should|pattern|String|field required/i.test(e.message)
        setError(e.codigo === 'razon_social' ? 'razonSocial' : 'ruc', { message: tecnico ? 'Revisa el RUC y la razón social.' : e.message })
      }
    }
  }

  return (
    <Modal titulo="Registrar proveedor" enCerrar={enCerrar}>
      <Form noValidate onSubmit={handleSubmit(guardar)} className="d-flex flex-column gap-3">
        <Form.Group controlId="alta-ruc">
          <Form.Label className="small fw-semibold">RUC o DNI</Form.Label>
          {/* register conecta el campo con el formulario: valor, cambios y validación */}
          <Form.Control {...register('ruc')} inputMode="numeric" autoFocus placeholder="RUC de 11 dígitos o DNI de 8" isInvalid={!!errors.ruc} />
          <Form.Control.Feedback type="invalid">{errors.ruc?.message}</Form.Control.Feedback>
        </Form.Group>
        {consultando && <span className="small text-body-secondary">Consultando en SUNAT…</span>}
        {avisoRuc && !errors.ruc && <Aviso tono="aviso" rol="status">{avisoRuc}</Aviso>}
        {sunat && (
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <Chip tono={sunat.apto_para_comprar ? 'bien' : 'aviso'}>
              SUNAT: {sunat.estado_sunat} · {sunat.condicion}
            </Chip>
            <span className="small text-body-secondary">razón social traída del padrón</span>
          </div>
        )}
        <Form.Group controlId="alta-razon">
          <Form.Label className="small fw-semibold">Razón social</Form.Label>
          <Form.Control {...register('razonSocial')} isInvalid={!!errors.razonSocial} />
          <Form.Control.Feedback type="invalid">{errors.razonSocial?.message}</Form.Control.Feedback>
        </Form.Group>
        <p className="small text-body-secondary">
          Con los 11 dígitos del RUC, la razón social se trae sola del padrón de
          SUNAT (vía el servicio de integraciones). Si el servicio no responde,
          se escribe a mano y el proveedor queda «sin validar»: la compra no se
          bloquea.
        </p>
        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enviar deshabilitado={isSubmitting}>
            {isSubmitting ? 'Guardando…' : 'Guardar proveedor'}
          </Pildora>
        </div>
      </Form>
    </Modal>
  )
}

/* ── HU-29 · T-344: la baja es un estado con fecha; sus compras quedan y deja de salir en compras nuevas ── */
function BajaProveedor({ p, enCerrar, enListo }: { p: Proveedor; enCerrar: () => void; enListo: () => void }) {
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const confirmar = async () => {
    setGuardando(true); setError('')
    try { await proveedoresOficina.darDeBaja(p.id); enListo() }
    catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo dar de baja.') }
    finally { setGuardando(false) }
  }
  return (
    <Modal titulo="Dar de baja al proveedor" enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <p>
          <strong>{p.razonSocial}</strong> (RUC {p.ruc}) dejará de aparecer al registrar compras nuevas.
          No se borra: sus {p.comprasRegistradas} compras registradas se conservan y sigue en esta lista como «De baja».
        </p>
        {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={confirmar} deshabilitado={guardando}>{guardando ? 'Dando de baja…' : 'Dar de baja'}</Pildora>
        </div>
      </div>
    </Modal>
  )
}

export function Proveedores() {
  const [todos, setTodos] = useState<Proveedor[] | null>(null)
  const [filtro, setFiltro] = useState<'activos' | 'baja' | 'todos'>('activos')
  const [altaAbierta, setAltaAbierta] = useState(false)
  const [bajando, setBajando] = useState<Proveedor | null>(null)
  const [validandoId, setValidandoId] = useState<string | null>(null)
  const [avisoValidacion, setAvisoValidacion] = useState<{ tono: 'bien' | 'aviso' | 'grave'; texto: string } | null>(null)

  const cargar = () => api.listarProveedores().then(setTodos)
  useEffect(() => {
    cargar()
  }, [])

  const revalidar = async (p: Proveedor) => {
    setValidandoId(p.id)
    setAvisoValidacion(null)
    try {
      const actualizado = await api.validarProveedor(p.id)
      setTodos((prev) => prev ? prev.map((x) => (x.id === p.id ? actualizado : x)) : prev)
      if (actualizado.estado === 'validado') {
        setAvisoValidacion({ tono: 'bien', texto: `${actualizado.razonSocial}: validado en SUNAT.` })
      } else if (actualizado.estado === 'con_alerta_sunat') {
        setAvisoValidacion({
          tono: 'aviso',
          texto: `${actualizado.razonSocial}: con alerta SUNAT (${actualizado.alertaSunat ?? 'revisar condición'}).`,
        })
      } else {
        setAvisoValidacion({
          tono: 'aviso',
          texto: `${actualizado.razonSocial}: ${actualizado.advertencia ?? 'no se pudo completar la consulta SUNAT.'}`,
        })
      }
    } catch (e) {
      setAvisoValidacion({ tono: 'grave', texto: e instanceof ErrorApi ? e.message : 'No se pudo validar en SUNAT.' })
    } finally {
      setValidandoId(null)
    }
  }

  const filtrados = useMemo(() => {
    if (!todos) return null
    const lista = filtro === 'todos' ? todos : todos.filter((p) => (p.estado === 'de_baja') === (filtro === 'baja'))
    // los que tienen historia de compras van arriba hasta que se ordene por una columna
    return [...lista].sort((a, b) => b.comprasRegistradas - a.comprasRegistradas)
  }, [todos, filtro])
  const puedeRegistrar = puedo('proveedor.registrar')

  return (
    <>
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado
        titulo="Proveedores"
        sub="RUC único en todo el sistema."
        acciones={puedeRegistrar && <Pildora enClic={() => setAltaAbierta(true)}>Registrar proveedor</Pildora>}
      />

      <Chips>
        <Chip tono="marca">Proveedores reales del cliente{todos ? ` · ${todos.length.toLocaleString('es-PE')}` : ''}</Chip>
        {usaApiReal
          ? <Chip tono="info">Compras y última compra: del servidor · días de entrega: aún sin registrar</Chip>
          : <Chip tono="aviso">Compras, entrega y última compra: datos de ejemplo</Chip>}
        <Chip tono="neutro">Un proveedor de baja sigue en la lista, pero no aparece al registrar compras nuevas</Chip>
        <Chip tono="neutro">El plazo de entrega alimenta el punto de reposición</Chip>
      </Chips>

      {avisoValidacion && <Aviso tono={avisoValidacion.tono} rol="status">{avisoValidacion.texto}</Aviso>}

      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Lista de proveedores">
        {filtrados === null ? <EsqueletoTabla /> : (
          <TablaDatos
            etiqueta="Proveedores"
            filas={filtrados}
            clave={(p) => p.id}
            buscar={(p) => `${p.razonSocial} ${p.ruc}`}
            marcadorBusqueda="Buscar por razón social o RUC…"
            filtros={<Segmentado etiqueta="Estado" valor={filtro} enCambio={setFiltro}
              opciones={[{ valor: 'activos', texto: 'Activos' }, { valor: 'baja', texto: 'De baja' }, { valor: 'todos', texto: 'Todos' }]} />}
            vacio={{ titulo: filtro === 'baja' ? 'Ningún proveedor de baja' : 'Aún no hay proveedores', detalle: 'Regístralo con su RUC: se valida en SUNAT.' }}
            columnas={[
              { clave: 'razon', titulo: 'Proveedor', valor: (p) => p.razonSocial, celda: (p) => <strong>{p.razonSocial}</strong> },
              { clave: 'ruc', titulo: 'RUC', valor: (p) => p.ruc, celda: (p) => <span className="text-body-secondary n">{p.ruc}</span> },
              { clave: 'estado', titulo: 'Estado', valor: (p) => ESTADO[p.estado].texto, celda: (p) => (
                <>
                  <Chip tono={ESTADO[p.estado].tono}>{ESTADO[p.estado].texto}</Chip>
                  {p.alertaSunat && <div className="small text-warning-emphasis mt-1">{p.alertaSunat}</div>}
                </>
              ) },
              { clave: 'compras', titulo: 'Compras', numerica: true, valor: (p) => p.comprasRegistradas, celda: (p) => p.comprasRegistradas },
              { clave: 'ultima', titulo: 'Última compra', numerica: true, valor: (p) => p.ultimaCompra, celda: (p) => <span className="text-nowrap">{p.ultimaCompra ?? '—'}</span> },
            ]}
            acciones={(p) => p.estado === 'de_baja' ? <span className="small text-body-secondary">De baja</span> : (
              <AccionesFila de={p.razonSocial} acciones={[
                /^\d{11}$/.test(p.ruc) && { texto: validandoId === p.id ? 'Validando…' : 'Validar en SUNAT', deshabilitado: validandoId === p.id, enClic: () => revalidar(p) },
                usaApiReal && puedeRegistrar && { texto: 'Dar de baja', peligro: true, enClic: () => setBajando(p) },
              ]} />
            )}
          />
        )}
      </Vidrio>

      {bajando && (
        <BajaProveedor p={bajando} enCerrar={() => setBajando(null)} enListo={() => { setBajando(null); cargar() }} />
      )}

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
