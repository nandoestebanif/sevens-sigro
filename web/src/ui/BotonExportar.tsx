/* Botón «Exportar ▾» (Dropdown de Bootstrap) con Excel y PDF. */
import { useState } from 'react'
import { Dropdown, Spinner } from 'react-bootstrap'
import { useAvisos } from './Avisos'
import { exportarExcel, exportarPdf, type ColumnaExport } from './exportar'

export function BotonExportar<T>({ titulo, subtitulo, columnas, filas, extra }: {
  titulo: string
  subtitulo: string
  columnas: ColumnaExport<T>[]
  filas: T[]
  /** más opciones al final del menú (p. ej. CSV) */
  extra?: { texto: string; accion: () => void }[]
}) {
  const [trabajando, setTrabajando] = useState(false)
  const avisar = useAvisos()
  const correr = async (tipo: 'Excel' | 'PDF') => {
    setTrabajando(true)
    try {
      if (tipo === 'Excel') await exportarExcel(titulo, columnas, filas)
      else await exportarPdf(titulo, subtitulo, columnas, filas)
      avisar(`${titulo}: ${filas.length.toLocaleString('es-PE')} filas exportadas a ${tipo}.`)
    } catch {
      avisar(`No se pudo generar el ${tipo}. Inténtalo de nuevo.`, 'aviso')
    } finally {
      setTrabajando(false)
    }
  }
  return (
    <Dropdown>
      <Dropdown.Toggle variant="outline-secondary" disabled={trabajando || filas.length === 0}>
        {trabajando ? <><Spinner size="sm" className="me-1" /> Generando…</> : 'Exportar'}
      </Dropdown.Toggle>
      <Dropdown.Menu align="end">
        <Dropdown.Item onClick={() => correr('Excel')}>Excel (.xlsx)</Dropdown.Item>
        <Dropdown.Item onClick={() => correr('PDF')}>PDF</Dropdown.Item>
        {extra?.map((e) => <Dropdown.Item key={e.texto} onClick={e.accion}>{e.texto}</Dropdown.Item>)}
      </Dropdown.Menu>
    </Dropdown>
  )
}
