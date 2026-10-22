/* ─────────────────────────────────────────────────────────────────────────────
   HU-08 · Como almacenero, quiero registrar un insumo con información
           estandarizada, para identificarlo de la misma manera en todas las
           obras.
   (Pantalla 16 del lienzo, «Catálogo e insumo»: consulta + alta + detalle.)

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
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { InsumoMaestro } from '../api/tipos'
import { Boton, Campo, Chip, Modal, Tarjeta, Titulo, Vacio } from '../ui/base'

const PASO = 60
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
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, padding: '9px 0', borderBottom: '1px solid var(--linea)' }}>
      <span style={{ fontSize: 12.5, color: 'var(--tinta-3)', fontWeight: 600 }}>{rot}</span>
      <span style={{ fontSize: 13, color: 'var(--tinta)', textAlign: 'right' }}>{children}</span>
    </div>
  )
}

function VerInsumo({ insumo, enCerrar, enEditar }: {
  insumo: InsumoMaestro
  enCerrar: () => void
  enEditar: () => void
}) {
  return (
    <Modal titulo={`Insumo · código ${insumo.codigo}`} enCerrar={enCerrar}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <FilaDetalle rot="Nombre normalizado">{insumo.nombre}{insumo.medida ? ` ${insumo.medida}` : ''}</FilaDetalle>
        <FilaDetalle rot="Unidad de medida">{insumo.um.toLowerCase()}</FilaDetalle>
        <FilaDetalle rot="Marcas registradas">{insumo.marca || '—'}</FilaDetalle>
        <FilaDetalle rot="Precio promedio">{precio(insumo.precioPromedio)}</FilaDetalle>
        <FilaDetalle rot="Estado">
          <Chip tono={insumo.estado === 'activo' ? 'bien' : 'aviso'}>
            {insumo.estado === 'activo' ? 'activo' : 'por verificar'}
          </Chip>
        </FilaDetalle>
        <p style={{ fontSize: 12, color: 'var(--tinta-3)', lineHeight: 1.5, marginTop: 10 }}>
          Las compras y el kárdex de este insumo se consultan en sus pantallas
          (HU-32 y HU-43); el precio promedio lo recalcula cada ingreso (NIC 2).
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cerrar</Boton>
          <Boton enClic={enEditar}>Editar</Boton>
        </div>
      </div>
    </Modal>
  )
}

function AltaInsumo({ unidades, editando, enCerrar, enListo }: {
  unidades: string[]
  editando: InsumoMaestro | null
  enCerrar: () => void
  enListo: () => void
}) {
  const [nombre, setNombre] = useState(editando?.nombre ?? '')
  const [medida, setMedida] = useState(editando?.medida ?? '')
  const [marca, setMarca] = useState(editando?.marca ?? '')
  const [um, setUm] = useState(editando?.um ?? 'UNIDADES')
  const [error, setError] = useState<{ campo: string; texto: string } | null>(null)
  const [guardando, setGuardando] = useState(false)
  const porVerificar = editando?.estado === 'por_verificar'

  const guardar = async (verificar = false) => {
    setGuardando(true)
    setError(null)
    try {
      if (editando) {
        await api.editarInsumo(editando.codigo, { nombre, marca, verificar })
      } else {
        await api.crearInsumoCatalogo({ nombre, um, medida, marca })
      }
      enListo()
    } catch (e) {
      if (e instanceof ErrorApi) {
        setError({ campo: e.codigo === 'um' ? 'um' : 'nombre', texto: e.message })
      }
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={editando ? `Editar insumo · código ${editando.codigo}` : 'Agregar insumo al catálogo'} enCerrar={enCerrar}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Campo
          id="ins-nombre"
          etiqueta="Material"
          valor={nombre}
          enCambio={setNombre}
          marcador="p. ej. IMPERMEABILIZANTE PARA CISTERNA"
          autoFoco
          error={error?.campo === 'nombre' ? error.texto : undefined}
        />
        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ flex: 1 }}>
            <Campo id="ins-medida" etiqueta="Medida (si el tipo la lleva)" valor={medida} enCambio={setMedida} marcador="p. ej. 1/2¨ x 9 m" deshabilitado={!!editando} />
          </div>
          <div style={{ flex: 1 }}>
            <Campo id="ins-marca" etiqueta="Marca (opcional)" valor={marca} enCambio={setMarca} marcador="p. ej. SIKA" />
          </div>
        </div>
        <div>
          <label htmlFor="ins-um" style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--tinta-3)', marginBottom: 6 }}>
            Unidad de medida
          </label>
          <select
            id="ins-um"
            value={um}
            onChange={(e) => setUm(e.target.value)}
            disabled={!!editando}
            style={{
              width: '100%', height: 42, border: '1px solid var(--borde)',
              borderRadius: 'var(--radio-chico)', padding: '0 12px', fontSize: 14,
              fontFamily: 'inherit', background: 'var(--tarjeta)', color: 'var(--tinta)',
            }}
          >
            {unidades.map((u) => <option key={u} value={u}>{u}</option>)}
          </select>
          {error?.campo === 'um' && (
            <div style={{ fontSize: 12, color: 'var(--grave)', marginTop: 5 }}>{error.texto}</div>
          )}
        </div>
        <p style={{ fontSize: 12.5, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
          {editando
            ? 'El código y la unidad no se editan nunca: los movimientos históricos los referencian. Se corrige el nombre o la marca.'
            : 'El código lo genera el sistema (único e irrepetible). Un insumo creado a mano nace «por verificar» hasta que el residente lo revisa. No se admite otro insumo con el mismo material, medida y marca.'}
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          {porVerificar && (
            <Boton tipo="secundario" enClic={() => guardar(false)} deshabilitado={guardando}>
              Guardar sin verificar
            </Boton>
          )}
          <Boton enClic={() => guardar(porVerificar)} deshabilitado={guardando}>
            {guardando ? 'Guardando…' : porVerificar ? 'Guardar y verificar' : editando ? 'Guardar cambios' : 'Agregar al catálogo'}
          </Boton>
        </div>
      </div>
    </Modal>
  )
}

export function Catalogo() {
  const [maestro, setMaestro] = useState<InsumoMaestro[] | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [um, setUm] = useState('todas')
  const [tope, setTope] = useState(PASO)
  const [editando, setEditando] = useState<InsumoMaestro | null>(null)
  const [viendo, setViendo] = useState<InsumoMaestro | null>(null)
  const [altaAbierta, setAltaAbierta] = useState(false)

  const cargar = () => api.listarMaestro().then(setMaestro)
  useEffect(() => {
    cargar()
  }, [])

  const unidades = useMemo(() => {
    const cuenta = new Map<string, number>()
    for (const i of maestro ?? []) cuenta.set(i.um, (cuenta.get(i.um) ?? 0) + 1)
    return Array.from(cuenta.entries()).sort((a, b) => b[1] - a[1])
  }, [maestro])

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    return (maestro ?? []).filter((i) =>
      (um === 'todas' || i.um === um) &&
      (!q || i.nombre.toLowerCase().includes(q) || i.medida.toLowerCase().includes(q) || i.codigo.includes(q)))
  }, [maestro, busqueda, um])

  const recorte = visibles.slice(0, tope)

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

  const accion = (texto: string, alClic: () => void) => (
    <button
      type="button"
      onClick={alClic}
      style={{
        border: '1px solid var(--borde)', background: 'var(--tarjeta)',
        color: 'var(--tinta-2)', borderRadius: 'var(--radio-pildora)',
        padding: '5px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer',
        fontFamily: 'inherit',
      }}
    >
      {texto}
    </button>
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
        <Titulo sub={`${(maestro?.length ?? 0).toLocaleString('es-PE')} insumos normalizados · el mismo nombre y código en todas las obras`}>
          Catálogo de insumos
        </Titulo>
        <div style={{ display: 'flex', gap: 10 }}>
          <Boton tipo="secundario" enClic={exportar} deshabilitado={!visibles.length}>Exportar</Boton>
          <Boton enClic={() => { setEditando(null); setAltaAbierta(true) }}>Agregar insumo</Boton>
        </div>
      </div>

      <Tarjeta estilo={{ padding: '12px 16px', borderLeft: '4px solid var(--marca)' }}>
        <span style={{ fontSize: 12.5, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
          <strong style={{ color: 'var(--tinta)' }}>El catálogo es de la empresa; la relación de insumos es de cada obra.</strong>{' '}
          Aquí vive el nombre normalizado, el código único y la unidad con que se compra.
          Lo que cada obra necesita viene de su propio S10 y se empareja contra este
          catálogo al importarlo. Del catálogo nada se elimina: un insumo mal creado se
          corrige, y la baja es un estado con fecha.
        </span>
      </Tarjeta>

      <div style={{ display: 'flex', gap: 10 }}>
        <input
          value={busqueda}
          onChange={(e) => { setBusqueda(e.target.value); setTope(PASO) }}
          placeholder="Buscar por nombre o código…"
          aria-label="Buscar insumo por nombre o código"
          style={{
            flex: 1, height: 42, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)',
            padding: '0 14px', fontSize: 14, fontFamily: 'inherit', background: 'var(--tarjeta)',
            color: 'var(--tinta)', outlineColor: 'var(--accion)',
          }}
        />
        <select
          aria-label="Filtrar por unidad de medida"
          value={um}
          onChange={(e) => { setUm(e.target.value); setTope(PASO) }}
          style={{
            height: 42, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)',
            padding: '0 14px', fontSize: 13, fontWeight: 600, fontFamily: 'inherit',
            background: 'var(--tarjeta)', color: 'var(--tinta-2)', maxWidth: 230,
          }}
        >
          <option value="todas">Todas las unidades</option>
          {unidades.map(([u, n]) => <option key={u} value={u}>{u} · {n}</option>)}
        </select>
      </div>

      <Tarjeta relleno={false}>
        {maestro === null ? (
          <Vacio titulo="Cargando…" detalle="Trayendo el catálogo de la empresa." />
        ) : visibles.length === 0 ? (
          <Vacio titulo="Ningún insumo coincide" detalle="Prueba con otro nombre u otro código, o agrégalo al catálogo." />
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                {['Código', 'Insumo', 'Unidad', 'Marca', 'Precio promedio', 'Acciones'].map((h, i) => (
                  <th key={h} style={{ padding: '11px 16px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: i >= 4 ? 'right' : 'left' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {recorte.map((ins, i) => (
                <tr key={ins.codigo} style={{ borderBottom: i === recorte.length - 1 ? 'none' : '1px solid var(--linea)' }}>
                  <td className="n" style={{ padding: '10px 16px', fontSize: 12.5, color: 'var(--tinta-3)', width: 70 }}>{ins.codigo}</td>
                  <td style={{ padding: '10px 16px', fontSize: 13.5, fontWeight: 600, color: 'var(--tinta)' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                      {amable(ins.medida ? `${ins.nombre} ${ins.medida}` : ins.nombre)}
                      {ins.estado === 'por_verificar' && <Chip tono="aviso">por verificar</Chip>}
                    </span>
                  </td>
                  <td style={{ padding: '10px 16px' }}><Chip tono="neutro">{ins.um.toLowerCase()}</Chip></td>
                  <td style={{ padding: '10px 16px', fontSize: 12, color: 'var(--tinta-3)' }}>{ins.marca || '—'}</td>
                  <td className="n" style={{ padding: '10px 16px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {precio(ins.precioPromedio)}
                  </td>
                  <td style={{ padding: '10px 16px', textAlign: 'right', whiteSpace: 'nowrap', width: 150 }}>
                    <span style={{ display: 'inline-flex', gap: 6 }}>
                      {accion('Ver', () => setViendo(ins))}
                      {accion(ins.estado === 'por_verificar' ? 'Verificar' : 'Editar', () => { setEditando(ins); setAltaAbierta(true) })}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {visibles.length > tope && (
          <div style={{ padding: 14, display: 'flex', justifyContent: 'center', borderTop: '1px solid var(--borde)' }}>
            <Boton tipo="secundario" enClic={() => setTope(tope + PASO)}>
              Mostrar 60 más · van {recorte.length.toLocaleString('es-PE')} de {visibles.length.toLocaleString('es-PE')}
            </Boton>
          </div>
        )}
      </Tarjeta>

      <p style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
        La excepción del acero se lee aquí: el catálogo lo registra como{' '}
        <strong>fierro corrugado por diámetro, en unidades de 9 m</strong> (así se compra),
        mientras el S10 lo planifica como un solo insumo en kilos. El factor de conversión
        del catálogo los empareja; nunca se convierte a mano.
      </p>

      {viendo && (
        <VerInsumo
          insumo={viendo}
          enCerrar={() => setViendo(null)}
          enEditar={() => { setEditando(viendo); setViendo(null); setAltaAbierta(true) }}
        />
      )}
      {altaAbierta && (
        <AltaInsumo
          unidades={unidades.length ? unidades.map(([u]) => u) : ['UNIDADES']}
          editando={editando}
          enCerrar={() => setAltaAbierta(false)}
          enListo={() => { setAltaAbierta(false); cargar() }}
        />
      )}
    </div>
  )
}
