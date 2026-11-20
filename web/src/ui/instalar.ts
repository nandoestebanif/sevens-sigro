/* PWA · «Instalar SIGRO»: el navegador avisa con el evento beforeinstallprompt cuando la web
   se puede instalar; se guarda ese aviso y se muestra el diálogo cuando el usuario lo pide. */
import { useEffect, useState } from 'react'

interface AvisoInstalacion extends Event { prompt: () => Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }

export function useInstalar() {
  const [aviso, setAviso] = useState<AvisoInstalacion | null>(null)
  useEffect(() => {
    const guardar = (e: Event) => { e.preventDefault(); setAviso(e as AvisoInstalacion) }
    const instalada = () => setAviso(null)
    window.addEventListener('beforeinstallprompt', guardar)
    window.addEventListener('appinstalled', instalada)
    return () => { window.removeEventListener('beforeinstallprompt', guardar); window.removeEventListener('appinstalled', instalada) }
  }, [])
  /** null si el navegador no lo permite (ya instalada, Safari, etc.) */
  return aviso && (async () => { await aviso.prompt(); await aviso.userChoice; setAviso(null) })
}
