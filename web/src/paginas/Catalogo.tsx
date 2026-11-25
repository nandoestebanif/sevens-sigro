/* ─────────────────────────────────────────────────────────────────────────────
   HU-08 · Como almacenero, quiero registrar un insumo con información
           estandarizada, para identificarlo de la misma manera en todas las
           obras.
   Lienzo W4 «Catálogo de insumos» (estilo vidrio): consulta + alta + detalle.

   Criterios de HU-08 cubiertos:
   - Material y unidad de medida son obligatorios.
   - El sistema genera un código único e irrepetible (correlativo del maestro).
   - La marca es opcional.
   - No se admite un insumo con el mismo material, medida y marca que otro.
   Y de las respuestas del cliente (22 oct):
   - Un insumo creado a mano nace «por verificar»; el residente corrige el
     nombre o la marca y lo verifica. El código no se edita nunca.
   - Del catálogo nada se borra (regla 10): no hay botón de eliminar a
     propósito; la baja —cuando llegue su historia— será un estado con fecha.

   La pantalla habla SOLO con la capa de API (api.listarMaestro / crearInsumo /
   editarInsumo): cuando el catálogo viva en PostgreSQL, esto no se toca.
   Los nombres normalizados van en MAYÚSCULAS en el maestro; para leer más
   cómodo, la tabla los muestra en caja amable y el detalle enseña el nombre
   normalizado exacto.
   ───────────────────────────────────────────────────────────────────────── */
import { useEnVivo } from '../formularios/enVivo'
import { aNumero, cantidadPositiva, minimo } from '../formularios/reglas'
import { puedo } from '../api/permisos'
import { fechaHoraLima } from '../api/horaLima'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { HistorialFactor, InsumoMaestro } from '../api/tipos'
import { Campo, Chip, Modal, Vacio } from '../ui/base'
import { Aviso, Chips, Encabezado, Pildora, TituloSeccion, Vidrio, Buscador } from '../ui/piezas'
import { FilasVirtuales, type MedirFila } from '../ui/FilasVirtuales'
import { UNIDADES_COMPRA } from '../api/unidades'
import { useAvisos } from '../ui/Avisos'
import { Ayuda } from '../ui/Glosario'
import { alternarOrden, ordenar, type Orden } from '../ui/tablas'
import { ThOrden } from '../ui/Tabla'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { BotonExportar } from '../ui/BotonExportar'

const CORTAS = new Set(['DE', 'LA', 'EL', 'Y', 'A', 'EN', 'SIN', 'CON', 'POR', 'DEL', 'LOS', 'LAS'])

/** MAYÚSCULAS → caja amable: baja las palabras comunes y conserva siglas,
    medidas y normas (PVC, KG, ASTM, 1/2¨…). Solo para mostrar: el nombre
    normalizado del maestro no se altera. */
function amable(nombre: string): string {
  const palabras = nombre.split(' ').map((p) => {
    if (CORTAS.has(p)) return p.toLowerCase()
    if (p.length >= 4 && /^[A-ZÁÉÍÓÚÑ]+$/.test(p)) return p.charAt(0) + p.slice(1).toLowerCase()
    return p
  })
  const texto = palabras.join(' ')
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

function precio(v: number): string {
  return v > 0 ? `S/ ${v.toLocaleString('es-PE', { minimumFractionDigits: 2 })}` : '—'
}

function FilaDetalle({ rot, children }: { rot: string; children: ReactNode }) {
  return (
    <div className="d-flex justify-content-between gap-3 py-2 px-0 border-bottom" >
      <span className="small text-body-secondary fw-semibold">{rot}</span>
      <span className="fw-semibold text-end">{children}</span>
    </div>
  )
}

function VerInsumo({ insumo, enCerrar, enEditar, enFactor }: {
  insumo: InsumoMaestro
  enCerrar: () => void
  enEditar: () => void
  enFactor: () => void
}) {
  const uCompra = insumo.um.toLowerCase()
  const uS10 = (insumo.unidadS10 ?? insumo.um).toLowerCase()
  const factor = insumo.factorConversion ?? 1
  return (
    <Modal titulo={`Insumo · código ${insumo.codigo}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-1">
        <FilaDetalle rot="Nombre normalizado">{insumo.nombre}{insumo.medida ? ` ${insumo.medida}` : ''}</FilaDetalle>
        <FilaDetalle rot="Unidad de compra">{uCompra}</FilaDetalle>
        <FilaDetalle rot="Unidad S10">{uS10}</FilaDetalle>
        <FilaDetalle rot="Factor de conversión">1 {uCompra} = {factor.toLocaleString('es-PE', { maximumFractionDigits: 6 })} {uS10}</FilaDetalle>
        <FilaDetalle rot="Norma técnica">{insumo.norma || '—'}</FilaDetalle>
        <FilaDetalle rot="Marcas registradas">{insumo.marca || '—'}</FilaDetalle>
        <FilaDetalle rot="Precio promedio">{precio(insumo.precioPromedio)}</FilaDetalle>
        <FilaDetalle rot="Estado">
          <Chip tono={insumo.estado === 'activo' ? 'bien' : 'aviso'}>
            {insumo.estado === 'activo' ? 'activo' : 'por verificar'}
          </Chip>
        </FilaDetalle>
        <p className="small text-body-secondary mt-3">
          Las compras y el kárdex de este insumo se consultan en sus pantallas
          (HU-32 y HU-43); el precio promedio lo recalcula cada ingreso (NIC 2).
        </p>
        <div className="d-flex flex-wrap gap-2 justify-content-end mt-2">
          <Pildora tipo="secundario" enClic={enCerrar}>Cerrar</Pildora>
          {puedo('insumo.factor') && <Pildora tipo="secundario" enClic={enFactor}>Factor de conversión</Pildora>}
          {puedo('insumo.registrar') && <Pildora enClic={enEditar}>Editar</Pildora>}
        </div>
      </div>
    </Modal>
  )
}

function ModalFactor({ insumo, enCerrar, enGuardado }: {
  insumo: InsumoMaestro
  enCerrar: () => void
  enGuardado: (actualizado: InsumoMaestro) => void
}) {
  const uCompra = insumo.um.toLowerCase()
  const uS10 = (insumo.unidadS10 ?? insumo.um).toLowerCase()
  const factorActual = insumo.factorConversion ?? 1
  const [factor, setFactor] = useState(String(factorActual))
  const [motivo, setMotivo] = useState('')
  const [historial, setHistorial] = useState<HistorialFactor[] | null>(null)
  const [error, setError] = useState('')
  const avisar = useAvisos()
  const [guardando, setGuardando] = useState(false)

  // useCallback: el historial se recarga solo al cambiar de insumo (y a mano, tras guardar un factor)
  const cargarHistorial = useCallback(() => {
    api.historialFactorConversion(insumo.codigo).then(setHistorial).catch(() => setHistorial([]))
  }, [insumo.codigo])
  useEffect(() => {
    cargarHistorial()
  }, [cargarHistorial])

  const factorNum = aNumero(factor)
  const valido = Number.isFinite(factorNum) && factorNum > 0
  // validación en vivo con las reglas del servidor (FactorIn: factor > 0 con hasta 6 decimales; motivo de 3 a 300)
  const v = useEnVivo({
    factor: cantidadPositiva(factor, 'Escribe el factor.') ?? (factorNum === factorActual ? 'Ese ya es el factor vigente.' : null),
    motivo: minimo(motivo, 3, 'Escribe el motivo del cambio (mínimo 3 letras).') ?? (motivo.trim().length > 300 ? 'Máximo 300 caracteres.' : null),
  })

  const guardar = async () => {
    if (v.alGuardar()) return
    setGuardando(true)
    setError('')
    try {
      const actualizado = await api.cambiarFactorConversion(insumo.codigo, factorNum, motivo)
      setMotivo(''); v.reiniciar()
      avisar(`Factor actualizado: 1 ${uCompra} = ${factorNum.toLocaleString('es-PE', { maximumFractionDigits: 6 })} ${uS10}.`)
      cargarHistorial()
      enGuardado(actualizado)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo guardar el factor de conversión.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Factor de conversión · ${insumo.codigo}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <div className="d-flex flex-column gap-1 py-2 px-3 bg-body-tertiary rounded-4" >
          <strong>{amable(insumo.medida ? `${insumo.nombre} ${insumo.medida}` : insumo.nombre)}</strong>
          <span className="n small text-body-secondary">
            Vigente: <strong>1 {uCompra} = {factorActual.toLocaleString('es-PE', { maximumFractionDigits: 6 })} {uS10}</strong>
          </span>
        </div>

        <Campo
          id="factor-valor"
          etiqueta={`Factor (${uS10} por cada ${uCompra})`}
          valor={factor}
          enCambio={(x) => { setFactor(x); v.cambio('factor') }}
          marcador="p. ej. 7.12"
          autoFoco
          modoTeclado="decimal"
          {...v.campo('factor')}
        />

        {valido && (
          <div className="small text-body-secondary bg-body-tertiary rounded-4 py-2 px-2" >
            Equivalencia: <strong className="n">1 {uCompra} = {factorNum.toLocaleString('es-PE', { maximumFractionDigits: 6 })} {uS10}</strong>
            {' · '}
            <span className="n">100 {uS10} ≈ {(100 / factorNum).toLocaleString('es-PE', { maximumFractionDigits: 2 })} {uCompra}</span>
          </div>
        )}

        <div>
          <label htmlFor="factor-motivo" className="d-block small fw-semibold text-body-secondary mb-2">
            Motivo del cambio (obligatorio)
          </label>
          <textarea
            id="factor-motivo"
            value={motivo}
            onChange={(e) => { setMotivo(e.target.value); v.cambio('motivo') }}
            onBlur={() => v.salir('motivo')}
            placeholder="p. ej. Acero corrugado 1/2&quot; × 9 m: 7.12 kg por varilla según ficha técnica"
            rows={2}
            className={`form-control py-2${v.errores.motivo ? ' is-invalid' : v.campo('motivo').valido ? ' is-valid' : ''}`}
            aria-invalid={!!v.errores.motivo} aria-describedby={v.errores.motivo ? 'factor-motivo-error' : undefined}
             style={{ height: 'auto', resize: 'vertical' }}
          />
          {v.errores.motivo && <div id="factor-motivo-error" className="invalid-feedback d-block" aria-live="polite">{v.errores.motivo}</div>}
        </div>

        <p className="small text-body-secondary">
          Sin factor declarado, el sistema asume 1 (ambas unidades iguales). Cada cambio se
          guarda en el histórico con fecha y usuario, sin borrar el valor anterior.
        </p>

        {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}

        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cerrar</Pildora>
          <Pildora enClic={guardar} deshabilitado={guardando}>
            {guardando ? 'Guardando…' : 'Guardar factor'}
          </Pildora>
        </div>

        <div className="pt-3 d-flex flex-column gap-2 border-top" >
          <span className="small fw-bold text-uppercase text-body-secondary">
            Histórico de cambios{historial && historial.length > 0 ? ` · ${historial.length} (el más reciente primero)` : ''}
          </span>
          {historial === null ? (
            <span className="small text-body-secondary">Consultando histórico…</span>
          ) : historial.length === 0 ? (
            <span className="small text-body-secondary">Sin cambios registrados; se usa el factor inicial (1).</span>
          ) : (
            <div className="d-flex flex-column gap-2" style={{ maxHeight: 160, overflowY: 'auto' }}>
              {[...historial].reverse().map((h) => (
                <div key={h.id} className="small bg-body-tertiary rounded-4 py-2 px-2" >
                  <div className="d-flex justify-content-between gap-2 fw-bold">
                    <span className="n">{h.factorAnterior} → {h.factorNuevo} {uS10}/{uCompra}</span>
                    <span className="n text-body-secondary fw-medium">{fechaHoraLima(h.cambiadoEn)}</span>
                  </div>
                  <div className="text-body-secondary mt-1">{h.motivo} · {h.cambiadoPor}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}

function AltaInsumo({ editando, enCerrar, enListo }: {
  editando: InsumoMaestro | null
  enCerrar: () => void
  enListo: (creado?: InsumoMaestro) => void
}) {
  const [nombre, setNombre] = useState(editando?.nombre ?? '')
  const [medida, setMedida] = useState(editando?.medida ?? '')
  const [marca, setMarca] = useState(editando?.marca ?? '')
  const [norma, setNorma] = useState(editando?.norma ?? '')
  const [um, setUm] = useState(editando?.um ?? 'und')
  // D08: unidades normalizadas; si el insumo que se edita trae otra (del S10), se muestra igual
  const opcionesUm = UNIDADES_COMPRA.some((u) => u.valor === um) ? UNIDADES_COMPRA : [{ valor: um, texto: um }, ...UNIDADES_COMPRA]
  const [error, setError] = useState<{ campo: string; texto: string } | null>(null)
  const [guardando, setGuardando] = useState(false)
  const porVerificar = editando?.estado === 'por_verificar'
  // validación en vivo con los límites del servidor (InsumoIn): material de 3 a 200; medida, norma y marca hasta 80
  const largo = (t: string) => (t.trim().length > 80 ? 'Máximo 80 caracteres.' : null)
  const v = useEnVivo({
    nombre: minimo(nombre, 3, 'Escribe el material (mínimo 3 letras).') ?? (nombre.trim().length > 200 ? 'Máximo 200 caracteres.' : null),
    medida: largo(medida), norma: largo(norma), marca: largo(marca),
  })
  const servidor = (campo: string) => (error?.campo === campo ? error.texto : undefined)

  const guardar = async (verificar = false) => {
    if (v.alGuardar()) return
    setGuardando(true)
    setError(null)
    try {
      if (editando) {
        await api.editarInsumo(editando.codigo, { nombre, marca, verificar })
        enListo()
      } else {
        enListo(await api.crearInsumoCatalogo({ nombre, um, medida, norma, marca }))
      }
    } catch (e) {
      setError({ campo: e instanceof ErrorApi && e.codigo === 'um' ? 'um' : 'nombre',
        texto: e instanceof ErrorApi ? e.message : 'No se pudo guardar el insumo. Inténtalo de nuevo.' })
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={editando ? `Editar insumo · código ${editando.codigo}` : 'Agregar insumo al catálogo'} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <Campo
          id="ins-nombre"
          etiqueta="Material"
          valor={nombre}
          enCambio={(x) => { setNombre(x); v.cambio('nombre'); setError(null) }}
          marcador="p. ej. IMPERMEABILIZANTE PARA CISTERNA"
          autoFoco
          {...v.campo('nombre')}
          error={servidor('nombre') ?? v.errores.nombre}
        />
        <div className="d-flex flex-wrap gap-3">
          <div className="min-w-0" style={{ flex: '1 1 160px' }}>
            <Campo id="ins-medida" etiqueta="Medida (si el tipo la lleva)" valor={medida} enCambio={(x) => { setMedida(x); v.cambio('medida') }} marcador="p. ej. 1/2¨ x 9 m" deshabilitado={!!editando} error={v.errores.medida} enSalir={() => v.salir('medida')} />
          </div>
          <div className="min-w-0" style={{ flex: '1 1 160px' }}>
            <Campo id="ins-norma" etiqueta="Norma (opcional)" valor={norma} enCambio={(x) => { setNorma(x); v.cambio('norma') }} marcador="p. ej. NTP 399.003 · ASTM A615" deshabilitado={!!editando} error={v.errores.norma} enSalir={() => v.salir('norma')} />
          </div>
          <div className="min-w-0" style={{ flex: '1 1 160px' }}>
            <Campo id="ins-marca" etiqueta="Marca (opcional)" valor={marca} enCambio={(x) => { setMarca(x); v.cambio('marca') }} marcador="p. ej. SIKA" error={v.errores.marca} enSalir={() => v.salir('marca')} />
          </div>
        </div>
        <div>
          <label htmlFor="ins-um" className="d-block small fw-semibold text-body-secondary mb-2">
            Unidad de medida
          </label>
          <select
            id="ins-um"
            value={um}
            onChange={(e) => setUm(e.target.value)}
            disabled={!!editando}
            className="form-select"
            style={{ height: 44, opacity: editando ? 0.6 : 1 }}
          >
            {opcionesUm.map((u) => <option key={u.valor} value={u.valor}>{u.texto}</option>)}
          </select>
          {error?.campo === 'um' && (
            <div className="small fw-semibold text-danger mt-2">{error.texto}</div>
          )}
        </div>
        <p className="small text-body-secondary">
          {editando
            ? 'El código y la unidad no se editan nunca: los movimientos históricos los referencian. Se corrige el nombre o la marca.'
            : 'El código lo genera el sistema (único e irrepetible). Un insumo creado a mano nace «por verificar» hasta que el residente lo revisa. No se admite otro insumo con el mismo material, medida y marca.'}
        </p>
        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          {porVerificar && (
            <Pildora tipo="secundario" enClic={() => guardar(false)} deshabilitado={guardando}>
              Guardar sin verificar
            </Pildora>
          )}
          <Pildora enClic={() => guardar(porVerificar)} deshabilitado={guardando}>
            {guardando ? 'Guardando…' : porVerificar ? 'Guardar y verificar' : editando ? 'Guardar cambios' : 'Agregar al catálogo'}
          </Pildora>
        </div>
      </div>
    </Modal>
  )
}

/* orden por columna del catálogo: fuera del componente, así useMemo no depende de una función nueva en cada render */
type ColCatalogo = 'codigo' | 'nombre' | 'um' | 'marca' | 'precio'
const valorCol = (i: InsumoMaestro, k: ColCatalogo): string | number =>
  k === 'codigo' ? i.codigo : k === 'nombre' ? i.nombre : k === 'um' ? i.um : k === 'marca' ? i.marca : i.precioPromedio

export function Catalogo() {
  const [maestro, setMaestro] = useState<InsumoMaestro[] | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [um, setUm] = useState('todas')
  const [editando, setEditando] = useState<InsumoMaestro | null>(null)
  const [viendo, setViendo] = useState<InsumoMaestro | null>(null)
  const [factorDe, setFactorDe] = useState<InsumoMaestro | null>(null)
  const [altaAbierta, setAltaAbierta] = useState(false)
  const avisar = useAvisos()

  const cargar = () => api.listarMaestro().then(setMaestro)
  useEffect(() => {
    cargar()
  }, [])

  const unidades = useMemo(() => {
    const cuenta = new Map<string, number>()
    for (const i of maestro ?? []) cuenta.set(i.um, (cuenta.get(i.um) ?? 0) + 1)
    return Array.from(cuenta.entries()).sort((a, b) => b[1] - a[1])
  }, [maestro])

  // orden por columna (encabezados con ▲▼): se filtra y luego se ordena; la tabla sigue virtualizada
  const [orden, setOrden] = useState<Orden<ColCatalogo> | null>(null)
  const enOrdenar = (k: ColCatalogo) => setOrden((o) => alternarOrden(o, k))

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    const filtrados = (maestro ?? []).filter((i) =>
      (um === 'todas' || i.um === um) &&
      (!q || i.nombre.toLowerCase().includes(q) || i.medida.toLowerCase().includes(q) || i.codigo.includes(q)))
    return ordenar(filtrados, orden, valorCol)
  }, [maestro, busqueda, um, orden])

  // seguridad: un texto que empieza con = + - @ se neutraliza con apóstrofo,
  // para que Excel no lo ejecute como fórmula al abrir el CSV
  const celdaSegura = (v: string) => `"${(/^[=+\-@]/.test(v) ? `'${v}` : v).replace(/"/g, '""')}"`

  const exportar = () => {
    const cab = 'codigo,insumo,um,marca,precio_promedio,estado'
    const cuerpo = visibles.map((i) =>
      [i.codigo, celdaSegura(i.medida ? `${i.nombre} ${i.medida}` : i.nombre), i.um, celdaSegura(i.marca),
        i.precioPromedio, i.estado].join(','))
    const blob = new Blob([[cab, ...cuerpo].join('\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `catalogo-insumos-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return (
    <>
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado
        titulo="Catálogo de insumos"
        sub={`${(maestro?.length ?? 0).toLocaleString('es-PE')} insumos normalizados · el mismo nombre y código en todas las obras`}
        acciones={
          <>
            <BotonExportar
              titulo="Catálogo de insumos"
              subtitulo={`${visibles.length.toLocaleString('es-PE')} insumos según el filtro y el orden de la pantalla`}
              filas={visibles}
              columnas={[
                { titulo: 'Código', valor: (i) => i.codigo, ancho: 10 },
                { titulo: 'Insumo', valor: (i) => (i.medida ? `${i.nombre} ${i.medida}` : i.nombre), ancho: 50 },
                { titulo: 'Unidad', valor: (i) => i.um, ancho: 9 },
                { titulo: 'Marca', valor: (i) => i.marca },
                { titulo: 'Precio promedio (S/)', valor: (i) => i.precioPromedio, formato: 'soles' },
                { titulo: 'Estado', valor: (i) => (i.estado === 'activo' ? 'Activo' : 'Por verificar') },
              ]}
              extra={[{ texto: 'CSV', accion: exportar }]}
            />
            {puedo('insumo.registrar') && <Pildora enClic={() => { setEditando(null); setAltaAbierta(true) }}>Agregar insumo</Pildora>}
          </>
        }
      />

      <Chips>
        <Chip tono="marca">Catálogo real del cliente: nombres, unidades, marcas y precios</Chip>
        <Chip tono="neutro">Código único en todas las obras</Chip>
      </Chips>

      <div className="d-flex flex-wrap gap-3 align-items-start">
        <Vidrio estilo={{ borderLeft: '4px solid var(--accion)', padding: '14px 20px', flex: '999 1 520px' }} etiqueta="El catálogo y la relación de cada obra">
          <p className="text-body-secondary">
            <strong>El catálogo es de la empresa; la relación de insumos es de cada obra.</strong>{' '}
            Aquí vive el nombre normalizado, el código único y la unidad con que se compra.
            Lo que cada obra necesita viene de su propio S10 y se empareja contra este
            catálogo al importarlo. Del catálogo nada se elimina: un insumo mal creado se
            corrige, y la baja es un estado con fecha.
          </p>
        </Vidrio>
        <aside className="card card-body shadow d-flex flex-column gap-3 min-w-0 rounded-3 py-3 px-4"  style={{ flex: '1 1 300px', boxShadow: 'var(--vidrio-sombra-alta)' }}>
          <span className="small fw-bold text-uppercase text-body-secondary">Factor de conversión <Ayuda clave="factor de conversión" /></span>
          <TituloSeccion>La excepción del acero</TituloSeccion>
          <p className="text-body-secondary">
            El catálogo lo registra como{' '}
            <strong>fierro corrugado por diámetro, en unidades de 9 m</strong> (así se compra),
            mientras el S10 lo planifica como un solo insumo en kilos (36 357 kg). El factor de conversión
            del catálogo los empareja; nunca se convierte a mano.
          </p>
          <div className="d-flex flex-column gap-1 bg-body-tertiary rounded-4 py-2 px-3" >
            <span className="small text-body-secondary">Ejemplo</span>
            <span className="n fs-6 fw-bold">1 varilla = [factor] kg</span>
            <span className="small text-body-secondary">Compara lo requerido, comprado y consumido en la misma unidad.</span>
          </div>
        </aside>
      </div>

      <div>
        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Insumos del catálogo">
          <div className="d-flex flex-wrap gap-2">
            <Buscador valor={busqueda} enCambio={setBusqueda} marcador="Buscar por nombre o código…" etiqueta="Buscar insumo por nombre o código" estilo={{ flex: '1 1 260px', width: 'auto' }} />
            <select
              aria-label="Filtrar por unidad de medida"
              value={um}
              onChange={(e) => { setUm(e.target.value) }}
              className="form-select fw-semibold rounded-pill"
               style={{ flex: '0 1 240px', height: 52 }}
            >
              <option value="todas">Todas las unidades</option>
              {unidades.map(([u, n]) => <option key={u} value={u}>{u} · {n}</option>)}
            </select>
          </div>

          {maestro === null ? (
            <EsqueletoTabla />
          ) : visibles.length === 0 ? (
            <Vacio titulo="Ningún insumo coincide" detalle="Prueba con otro nombre u otro código, o agrégalo al catálogo." />
          ) : (
            <div className="overflow-x-auto">
              <table className="table table-hover align-middle mb-0" style={{ tableLayout: 'fixed', minWidth: 860 }}>
                {/* anchos fijos: con la lista virtual las filas entran y salen, y sin esto las columnas bailarían */}
                <colgroup>
                  <col style={{ width: 100 }} /><col /><col style={{ width: 130 }} /><col style={{ width: 120 }} /><col style={{ width: 135 }} /><col style={{ width: 235 }} />
                </colgroup>
                <thead>
                  <tr>
                    <ThOrden clave="codigo" orden={orden} enOrdenar={enOrdenar}>Código</ThOrden>
                    <ThOrden clave="nombre" orden={orden} enOrdenar={enOrdenar}>Insumo</ThOrden>
                    <ThOrden clave="um" orden={orden} enOrdenar={enOrdenar}>Unidad</ThOrden>
                    <ThOrden clave="marca" orden={orden} enOrdenar={enOrdenar}>Marca</ThOrden>
                    <ThOrden clave="precio" orden={orden} enOrdenar={enOrdenar} className="n">Precio promedio</ThOrden><th className="text-end">Acciones</th>
                  </tr>
                </thead>
                {/* las 2 243 filas, pero en el DOM solo las que se ven (antes: de 60 en 60 con «Mostrar 60 más») */}
                <FilasVirtuales items={visibles} columnas={6} fila={(ins, _i, medir) => (
                  <FilaInsumo
                    key={ins.codigo}
                    insumo={ins}
                    medir={medir}
                    enVer={() => setViendo(ins)}
                    enFactor={() => setFactorDe(ins)}
                    enEditar={() => { setEditando(ins); setAltaAbierta(true) }}
                  />
                )} />
              </table>
            </div>
          )}
        </Vidrio>

      </div>

      {viendo && (
        <VerInsumo
          insumo={viendo}
          enCerrar={() => setViendo(null)}
          enFactor={() => { setFactorDe(viendo); setViendo(null) }}
          enEditar={() => { setEditando(viendo); setViendo(null); setAltaAbierta(true) }}
        />
      )}
      {factorDe && (
        <ModalFactor
          insumo={factorDe}
          enCerrar={() => setFactorDe(null)}
          enGuardado={(actualizado) => {
            cargar()
            setFactorDe(actualizado)
          }}
        />
      )}
      {altaAbierta && (
        <AltaInsumo
          editando={editando}
          enCerrar={() => setAltaAbierta(false)}
          enListo={(creado) => {
            setAltaAbierta(false); cargar()
            if (creado) avisar(`Insumo creado: ${creado.codigo} · ${[creado.nombre, creado.medida, creado.norma, creado.marca].filter(Boolean).join(' ')}. Queda «por verificar» hasta que el residente lo revise.`)
          }}
        />
      )}
    </div>
    </>
  )
}

/** Una fila del catálogo. Va aparte para que la lista pueda virtualizarse
    (envolver las filas) sin tocar cómo se dibuja cada una. */
function FilaInsumo({ insumo: ins, medir, enVer, enFactor, enEditar }: {
  insumo: InsumoMaestro; medir: MedirFila; enVer: () => void; enFactor: () => void; enEditar: () => void
}) {
  const tieneFactor = ins.factorConversion !== undefined && ins.factorConversion !== 1
  return (
    <tr {...medir}>
      <td className="n fw-bold text-start" style={{ color: 'var(--marca-tinte-tinta)', width: 70 }}>{ins.codigo}</td>
      <td className="fw-semibold" style={{ minWidth: 240 }}>
        <span className="d-inline-flex align-items-center gap-2 flex-wrap">
          {amable(ins.medida ? `${ins.nombre} ${ins.medida}` : ins.nombre)}
          {ins.estado === 'por_verificar' && <Chip tono="aviso">por verificar</Chip>}
        </span>
      </td>
      <td>
        <Chip tono="neutro">{ins.um.toLowerCase()}</Chip>
        {tieneFactor && (
          <div className="n small text-body-secondary mt-1 text-start">
            1 = {ins.factorConversion} {(ins.unidadS10 ?? ins.um).toLowerCase()}
          </div>
        )}
      </td>
      <td className="text-body-secondary">{ins.marca || '—'}</td>
      <td className="n text-nowrap">{precio(ins.precioPromedio)}</td>
      <td className="text-end text-nowrap" style={{ width: 235 }}>
        <span className="d-inline-flex gap-2">
          <Pildora tipo="secundario" chica enClic={enVer}>Ver</Pildora>
          {puedo('insumo.factor') && <Pildora tipo="secundario" chica enClic={enFactor}>Factor</Pildora>}
          {puedo(ins.estado === 'por_verificar' ? 'insumo.verificar' : 'insumo.registrar') && <Pildora tipo="secundario" chica enClic={enEditar}>{ins.estado === 'por_verificar' ? 'Verificar' : 'Editar'}</Pildora>}
        </span>
      </td>
    </tr>
  )
}

