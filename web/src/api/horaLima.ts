/* Las horas del servidor llegan en UTC (ISO «…Z» o el texto de PostgreSQL «2026-11-19 17:39:12.1+00»).
   La obra cuenta el tiempo en Lima (UTC−5, sin horario de verano): toda hora que se muestra pasa por aquí.
   Re-prueba de Nicolás (19/11): el historial del factor y los ajustes mostraban 17:35 cuando eran las 12:35. */

function aFecha(texto: string): Date | null {
  let t = texto.trim().replace(' ', 'T')
  t = t.replace(/([+-]\d{2})$/, '$1:00') // «+00» de PostgreSQL → «+00:00»
  const d = new Date(t)
  return Number.isNaN(d.getTime()) ? null : d
}

const partes = (d: Date) => {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(d).map((x) => [x.type, x.value]))
  return p as Record<'year' | 'month' | 'day' | 'hour' | 'minute', string>
}

/** «2026-11-19 12:35» en hora de Lima. Si el texto no es una fecha, lo devuelve como vino. */
export function fechaHoraLima(texto: string): string {
  const d = aFecha(texto)
  if (!d) return texto.slice(0, 16).replace('T', ' ')
  const p = partes(d)
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`
}

/** «19/11 12:35» en hora de Lima (kárdex). */
export function diaHoraLima(texto: string): string {
  const d = aFecha(texto)
  if (!d) return `${texto.slice(8, 10)}/${texto.slice(5, 7)} ${texto.slice(11, 16)}`
  const p = partes(d)
  return `${p.day}/${p.month} ${p.hour}:${p.minute}`
}
