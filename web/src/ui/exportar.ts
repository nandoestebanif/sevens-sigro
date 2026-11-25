/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · exportar una tabla a Excel (.xlsx) o PDF

   - Excel con write-excel-file: los números van como NÚMEROS (se pueden sumar en
     Excel), no como texto; el encabezado en negrita.
   - PDF con jsPDF + jspdf-autotable: título, fecha de corte, tabla paginada y
     número de página.
   Las dos librerías se cargan con import() dinámico: solo se descargan al
   exportar, no al abrir la página.
   ───────────────────────────────────────────────────────────────────────── */

export interface ColumnaExport<T> {
  titulo: string
  valor: (fila: T) => string | number | null
  /** número con 2 decimales (soles) o entero */
  formato?: 'soles' | 'entero' | 'decimal'
  ancho?: number
}

const hoy = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })

/** Nombre de archivo seguro: «inventario-valorizado-2026-11-20.xlsx» */
export function nombreArchivo(base: string, ext: 'xlsx' | 'pdf') {
  const limpio = base.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
  return `${limpio}-${hoy()}.${ext}`
}

/** Celdas que empiezan con = + - @ podrían ejecutarse como fórmula en Excel: se neutralizan. */
export const textoSeguro = (v: string) => (/^[=+\-@]/.test(v) ? `'${v}` : v)

export async function exportarExcel<T>(titulo: string, columnas: ColumnaExport<T>[], filas: T[]) {
  const { default: escribir } = await import('write-excel-file/browser')
  // encabezado negro con letras amarillas: los colores de la marca (rediseño 24/11)
  const encabezado = columnas.map((c) => ({ value: c.titulo, fontWeight: 'bold' as const, color: '#FAD85D', backgroundColor: '#161717' }))
  const cuerpo = filas.map((f) => columnas.map((c) => {
    const v = c.valor(f)
    if (v === null || v === '') return null
    if (typeof v === 'number') return { value: v, type: Number, format: c.formato === 'soles' ? '#,##0.00' : c.formato === 'entero' ? '#,##0' : '#,##0.###' }
    return { value: textoSeguro(v), type: String }
  }))
  await escribir([encabezado, ...cuerpo], {
    sheet: titulo.slice(0, 31), // Excel no admite nombres de hoja de más de 31 caracteres
    columns: columnas.map((c) => ({ width: c.ancho ?? 16 })),
  }).toFile(nombreArchivo(titulo, 'xlsx'))
}

/** El logo de SEVEN'S como data URL para jsPDF; si no se puede leer, el PDF sale sin logo (nunca falla por esto). */
async function cargarLogo(): Promise<string | null> {
  try {
    const { default: url } = await import('../assets/sevens-logo.png')
    const blob = await (await fetch(url)).blob()
    return await new Promise<string>((ok, mal) => {
      const r = new FileReader()
      r.onload = () => ok(String(r.result)); r.onerror = mal
      r.readAsDataURL(blob)
    })
  } catch { return null }
}

export async function exportarPdf<T>(titulo: string, subtitulo: string, columnas: ColumnaExport<T>[], filas: T[]) {
  const [{ jsPDF }, { autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')])
  const doc = new jsPDF({ orientation: columnas.length > 5 ? 'landscape' : 'portrait', unit: 'pt', format: 'a4' })
  const fmt = (v: string | number | null, c: ColumnaExport<T>) => v === null ? '—'
    : typeof v === 'number' ? v.toLocaleString('es-PE', c.formato === 'soles' ? { minimumFractionDigits: 2, maximumFractionDigits: 2 } : { maximumFractionDigits: 3 }) : v
  // cabecera de la marca: franja negra con filete amarillo y el logo de SEVEN'S a la derecha
  const ancho = doc.internal.pageSize.getWidth()
  doc.setFillColor(22, 23, 23); doc.rect(0, 0, ancho, 6, 'F')
  doc.setFillColor(250, 216, 93); doc.rect(0, 6, ancho, 3, 'F')
  const logo = await cargarLogo()
  if (logo) doc.addImage(logo, 'PNG', ancho - 40 - 54, 18, 54, 38)
  doc.setFont('helvetica', 'bold'); doc.setFontSize(15); doc.setTextColor(22, 23, 23); doc.text(titulo, 40, 40)
  doc.setFont('helvetica', 'normal'); doc.setFontSize(9); doc.setTextColor(100); doc.text(`${subtitulo} · SIGRO · SEVENS INGENIEROS SAC · generado el ${hoy()}`, 40, 56)
  autoTable(doc, {
    startY: 70,
    head: [columnas.map((c) => c.titulo)],
    body: filas.map((f) => columnas.map((c) => fmt(c.valor(f), c))),
    styles: { fontSize: 8, cellPadding: 5, lineColor: [226, 226, 221] },
    headStyles: { fillColor: [22, 23, 23], textColor: [250, 216, 93], fontStyle: 'bold' }, // negro y amarillo de la marca
    alternateRowStyles: { fillColor: [246, 246, 243] },
    columnStyles: Object.fromEntries(columnas.map((c, i) => [i, { halign: c.formato ? 'right' : 'left' }])),
    didDrawPage: () => {
      const n = doc.getNumberOfPages()
      doc.setFontSize(8); doc.text(`pág. ${n}`, doc.internal.pageSize.getWidth() - 60, doc.internal.pageSize.getHeight() - 20)
    },
  })
  doc.save(nombreArchivo(titulo, 'pdf'))
}
