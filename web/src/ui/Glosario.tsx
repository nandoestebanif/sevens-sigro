/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · glosario con Tooltip y Popover de Bootstrap

   Los términos técnicos (costo promedio ponderado, desviación, punto de
   reposición…) se explican donde aparecen: al pasar el ratón o al enfocar con
   el teclado (Tooltip), o al tocar el ícono «?» (Popover, sirve en el celular).
   Las definiciones salen de las reglas del proyecto (CLAUDE.md), no se inventan.
   ───────────────────────────────────────────────────────────────────────── */
import type { ReactNode } from 'react'
import { Button, OverlayTrigger, Popover, Tooltip } from 'react-bootstrap'

export const GLOSARIO = {
  'costo promedio': 'Costo promedio ponderado: cada ingreso recalcula el promedio del insumo en ese almacén; cada salida toma el promedio vigente y lo congela (NIC 2).',
  'costo congelado': 'El costo de una salida es el promedio vigente en el instante en que se registró. Un ingreso posterior no lo cambia.',
  'desviación': 'Diferencia entre lo gastado y lo esperado a la fecha. Lo esperado es lo planificado repartido de forma lineal en el plazo de la obra (HU-57).',
  'avance del plazo': 'Qué parte del plazo de la obra ya pasó: semanas transcurridas sobre semanas totales.',
  'planificado': 'Lo que el S10 presupuesta para la obra. Ya incluye desperdicio y reserva: excederlo avisa, nunca bloquea.',
  'punto de reposición': 'Stock mínimo de un insumo: si el saldo baja de aquí, el sistema sugiere comprar.',
  'capital detenido': 'Valor del material que está en almacén y todavía no se consume: Σ cantidad × costo promedio, a la fecha de corte.',
  'saldo': 'El stock no se guarda en un campo: lo calcula el servidor sumando los movimientos del libro.',
  'reverso': 'Un movimiento nunca se edita ni se borra. Para corregirlo se registra otro inverso que lo referencia, con autor, fecha y motivo.',
  'factor de conversión': 'Cuántas unidades del S10 hay en una unidad de compra. Ej.: 1 varilla de 9 m = tantos kg de acero.',
  'por conciliar': 'Recibido, consumido y en stock no cuadran entre sí: se muestran, pero no como cifras definitivas.',
} as const
export type Clave = keyof typeof GLOSARIO

/** Palabra con subrayado punteado: al pasar el ratón o enfocarla muestra su definición (Tooltip). */
export function Termino({ clave, children }: { clave: Clave; children?: ReactNode }) {
  return (
    <OverlayTrigger placement="top" overlay={<Tooltip>{GLOSARIO[clave]}</Tooltip>}>
      {/* tabIndex=0: también se abre con el teclado (Tab) */}
      <span tabIndex={0} className="text-decoration-underline" style={{ textDecorationStyle: 'dotted', cursor: 'help' }}>
        {children ?? clave}
      </span>
    </OverlayTrigger>
  )
}

/** Ícono «?» que abre un Popover con la definición al tocarlo (mejor en pantallas táctiles). */
export function Ayuda({ clave, titulo }: { clave: Clave; titulo?: string }) {
  const pop = (
    <Popover>
      <Popover.Header as="h3">{titulo ?? clave.charAt(0).toUpperCase() + clave.slice(1)}</Popover.Header>
      <Popover.Body>{GLOSARIO[clave]}</Popover.Body>
    </Popover>
  )
  return (
    <OverlayTrigger trigger="click" placement="auto" overlay={pop} rootClose>
      <Button variant="link" size="sm" className="p-0 ms-1 align-baseline text-body-secondary text-decoration-none"
        aria-label={`Qué es ${titulo ?? clave}`}>ⓘ</Button>
    </OverlayTrigger>
  )
}
