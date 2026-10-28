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
