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
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { InsumoMaestro } from '../api/tipos'
import { Campo, Chip, Modal, Vacio } from '../ui/base'
import { Chips, Encabezado, Fondo, Pildora, TituloSeccion, Vidrio } from '../ui/vidrio'
import { FilasVirtuales, type MedirFila } from '../ui/FilasVirtuales'

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
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--separador)' }}>
      <span style={{ fontSize: 13, color: 'var(--tinta-2)', fontWeight: 600 }}>{rot}</span>
      <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--tinta)', textAlign: 'right' }}>{children}</span>
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
        <p style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5, marginTop: 10 }}>
          Las compras y el kárdex de este insumo se consultan en sus pantallas
          (HU-32 y HU-43); el precio promedio lo recalcula cada ingreso (NIC 2).
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
          <Pildora tipo="secundario" enClic={enCerrar}>Cerrar</Pildora>
          <Pildora enClic={enEditar}>Editar</Pildora>
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
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
          <div style={{ flex: '1 1 160px', minWidth: 0 }}>
            <Campo id="ins-medida" etiqueta="Medida (si el tipo la lleva)" valor={medida} enCambio={setMedida} marcador="p. ej. 1/2¨ x 9 m" deshabilitado={!!editando} />
          </div>
          <div style={{ flex: '1 1 160px', minWidth: 0 }}>
            <Campo id="ins-marca" etiqueta="Marca (opcional)" valor={marca} enCambio={setMarca} marcador="p. ej. SIKA" />
          </div>
        </div>
        <div>
          <label htmlFor="ins-um" style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)', marginBottom: 6 }}>
            Unidad de medida
          </label>
          <select
            id="ins-um"
            value={um}
            onChange={(e) => setUm(e.target.value)}
            disabled={!!editando}
            className="campo"
            style={{ height: 44, opacity: editando ? 0.6 : 1 }}
          >
            {unidades.map((u) => <option key={u} value={u}>{u}</option>)}
          </select>
          {error?.campo === 'um' && (
            <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--grave)', marginTop: 5 }}>{error.texto}</div>
          )}
        </div>
        <p style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
          {editando
            ? 'El código y la unidad no se editan nunca: los movimientos históricos los referencian. Se corrige el nombre o la marca.'
            : 'El código lo genera el sistema (único e irrepetible). Un insumo creado a mano nace «por verificar» hasta que el residente lo revisa. No se admite otro insumo con el mismo material, medida y marca.'}
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, justifyContent: 'flex-end' }}>
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

export function Catalogo() {
  const [maestro, setMaestro] = useState<InsumoMaestro[] | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [um, setUm] = useState('todas')
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
    <Fondo dibujo="catalogo" />
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Encabezado
        titulo="Catálogo de insumos"
        sub={`${(maestro?.length ?? 0).toLocaleString('es-PE')} insumos normalizados · el mismo nombre y código en todas las obras`}
        acciones={
          <>
            <Pildora tipo="secundario" enClic={exportar} deshabilitado={!visibles.length}>Exportar</Pildora>
            <Pildora enClic={() => { setEditando(null); setAltaAbierta(true) }}>Agregar insumo</Pildora>
          </>
        }
      />

      <Chips>
        <Chip tono="marca">Catálogo real del cliente: nombres, unidades, marcas y precios</Chip>
        <Chip tono="neutro">Código único en todas las obras</Chip>
      </Chips>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-start' }}>
        <Vidrio estilo={{ borderLeft: '4px solid var(--accion)', padding: '14px 20px', flex: '999 1 520px' }} etiqueta="El catálogo y la relación de cada obra">
          <p style={{ fontSize: 14, color: 'var(--tinta-2)', lineHeight: 1.55 }}>
            <strong style={{ color: 'var(--tinta)' }}>El catálogo es de la empresa; la relación de insumos es de cada obra.</strong>{' '}
            Aquí vive el nombre normalizado, el código único y la unidad con que se compra.
            Lo que cada obra necesita viene de su propio S10 y se empareja contra este
            catálogo al importarlo. Del catálogo nada se elimina: un insumo mal creado se
            corrige, y la baja es un estado con fecha.
          </p>
        </Vidrio>
        <aside className="vidrio-fuerte" style={{ flex: '1 1 300px', minWidth: 0, borderRadius: 'var(--radio-vidrio)', padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 12, boxShadow: 'var(--vidrio-sombra-alta)' }}>
          <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--tinta-2)' }}>Factor de conversión</span>
          <TituloSeccion>La excepción del acero</TituloSeccion>
          <p style={{ fontSize: 14, color: 'var(--tinta-2)', lineHeight: 1.55 }}>
            El catálogo lo registra como{' '}
            <strong style={{ color: 'var(--tinta)' }}>fierro corrugado por diámetro, en unidades de 9 m</strong> (así se compra),
            mientras el S10 lo planifica como un solo insumo en kilos (36 357 kg). El factor de conversión
            del catálogo los empareja; nunca se convierte a mano.
          </p>
          <div style={{ background: 'var(--vidrio-celda)', borderRadius: 16, padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>Ejemplo</span>
            <span className="n" style={{ fontSize: 17, fontWeight: 800 }}>1 varilla = [factor] kg</span>
            <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>Compara lo requerido, comprado y consumido en la misma unidad.</span>
          </div>
        </aside>
      </div>

      <div>
        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Insumos del catálogo">
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            <label className="vidrio-fuerte" style={{ flex: '1 1 260px', minWidth: 0, height: 52, borderRadius: 26, padding: '0 18px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accion)" strokeWidth="2.4" strokeLinecap="round" style={{ flex: 'none' }}>
                <circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" />
              </svg>
              <input
                value={busqueda}
                onChange={(e) => { setBusqueda(e.target.value) }}
                placeholder="Buscar por nombre o código…"
                aria-label="Buscar insumo por nombre o código"
                className="foco-vidrio"
                style={{ flex: 1, minWidth: 0, height: 40, border: 0, background: 'transparent', fontSize: 15, fontFamily: 'inherit', color: 'var(--tinta)' }}
              />
            </label>
            <select
              aria-label="Filtrar por unidad de medida"
              value={um}
              onChange={(e) => { setUm(e.target.value) }}
              className="campo"
              style={{ flex: '0 1 240px', height: 52, borderRadius: 26, fontWeight: 600, fontSize: 14 }}
            >
              <option value="todas">Todas las unidades</option>
              {unidades.map(([u, n]) => <option key={u} value={u}>{u} · {n}</option>)}
            </select>
          </div>

          {maestro === null ? (
            <Vacio titulo="Cargando…" detalle="Trayendo el catálogo de la empresa." />
          ) : visibles.length === 0 ? (
            <Vacio titulo="Ningún insumo coincide" detalle="Prueba con otro nombre u otro código, o agrégalo al catálogo." />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio" style={{ tableLayout: 'fixed', minWidth: 860 }}>
                {/* anchos fijos: con la lista virtual las filas entran y salen, y sin esto las columnas bailarían */}
                <colgroup>
                  <col style={{ width: 100 }} /><col /><col style={{ width: 120 }} /><col style={{ width: 130 }} /><col style={{ width: 140 }} /><col style={{ width: 170 }} />
                </colgroup>
                <thead>
                  <tr>
                    <th>Código</th><th>Insumo</th><th>Unidad</th><th>Marca</th>
                    <th className="n">Precio promedio</th><th style={{ textAlign: 'right' }}>Acciones</th>
                  </tr>
                </thead>
                {/* las 2 243 filas, pero en el DOM solo las que se ven (antes: de 60 en 60 con «Mostrar 60 más») */}
                <FilasVirtuales items={visibles} columnas={6} fila={(ins, _i, medir) => (
                  <FilaInsumo
                    key={ins.codigo}
                    insumo={ins}
                    medir={medir}
                    enVer={() => setViendo(ins)}
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
    </>
  )
}

/** Una fila del catálogo. Va aparte para que la lista pueda virtualizarse
    (envolver las filas) sin tocar cómo se dibuja cada una. */
function FilaInsumo({ insumo: ins, medir, enVer, enEditar }: { insumo: InsumoMaestro; medir: MedirFila; enVer: () => void; enEditar: () => void }) {
  return (
    <tr {...medir}>
      <td className="n" style={{ fontWeight: 800, color: 'var(--marca-tinte-tinta)', textAlign: 'left', width: 70 }}>{ins.codigo}</td>
      <td style={{ fontWeight: 600, minWidth: 240 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          {amable(ins.medida ? `${ins.nombre} ${ins.medida}` : ins.nombre)}
          {ins.estado === 'por_verificar' && <Chip tono="aviso">por verificar</Chip>}
        </span>
      </td>
      <td><Chip tono="neutro">{ins.um.toLowerCase()}</Chip></td>
      <td style={{ color: 'var(--tinta-2)' }}>{ins.marca || '—'}</td>
      <td className="n" style={{ whiteSpace: 'nowrap' }}>{precio(ins.precioPromedio)}</td>
      <td style={{ textAlign: 'right', whiteSpace: 'nowrap', width: 170 }}>
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Pildora tipo="secundario" chica enClic={enVer}>Ver</Pildora>
          <Pildora tipo="secundario" chica enClic={enEditar}>{ins.estado === 'por_verificar' ? 'Verificar' : 'Editar'}</Pildora>
        </span>
      </td>
    </tr>
  )
}

