/// <reference types="vite/client" />
interface ImportMetaEnv {
  /** URL de la API de SIGRO (ej. http://localhost:8000). Vacía: cliente simulado. */
  readonly VITE_API_URL?: string
  readonly VITE_INTEGRACIONES_URL?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
