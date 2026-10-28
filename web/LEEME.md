Carpeta de web. El contenido lo crea su responsable; mira CLAUDE.md y tu archivo en equipo/.

## Conectar con la API real (HT-04, 27/10)

La web habla con el servidor por `src/api/http.ts` (sesión, `snake_case` ↔ `camelCase` y errores
`{codigo, mensaje}`). Se activa con una variable:

```bash
cp .env.example .env.local        # y pon VITE_API_URL=http://localhost:8000
npm run dev
```

Con `VITE_API_URL` la web pide usuario y contraseña (la API debe tener `SIGRO_CORS=http://localhost:5173`).
Sin la variable sigue con el cliente simulado, así nadie queda bloqueado.

| Ya va al servidor | Sigue simulado hasta que exista su endpoint |
|---|---|
| Sesión (HU-12), obras y obra activa (HU-01), semanas (HU-03), proveedores: lista, búsqueda y alta (HU-29, HU-30) | Catálogo (HU-07, HU-08), compras y recepción (HU-32, HU-35: necesitan los insumos del catálogo en la base), kárdex y stock (HU-43, HU-45), reposición (HU-23 a HU-27), gasto en recursos (HU-57) |

Para pasar otro método al servidor: se sobreescribe en `ClienteHttp` (al final de `src/api/cliente.ts`) y
ninguna pantalla cambia.

### 28/10 · catálogo e importación del S10 conectados

- **Catálogo (HU-08):** lista, alta y verificación van a `/catalogo/insumos`. El código lo genera el servidor
  (`SIGRO-00001`…) y un nombre repetido con la misma unidad lo rechaza la API. Corregir el nombre o la marca
  todavía no existe en la API: la pantalla lo dice en lugar de fallar en silencio.
- **Importar del S10 (HU-07):** el microservicio lee el PDF y muestra la vista previa; el botón **«Aplicar al
  catálogo»** manda las filas a `/catalogo/insumos/importar-s10`. Probado con el PDF real: 284 creados la primera
  vez; la segunda, 0 creados y 284 sin cambios (no duplica).
- **Pendiente de decisión:** los 284 códigos del S10 y los 2 243 del catálogo del cliente son códigos distintos para
  muchos de los mismos materiales. Falta emparejarlos (qué código manda) antes de usarlos en compras.
