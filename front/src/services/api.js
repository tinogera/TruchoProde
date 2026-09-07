// Punto unico de entrada al backend. Vite redirige /api al puerto 8080 (ver vite.config.js).
const BASE = '/api'

export async function pedir(ruta, opciones = {}) {
  const respuesta = await fetch(`${BASE}${ruta}`, {
    headers: { 'Content-Type': 'application/json', ...opciones.headers },
    ...opciones,
  })

  if (!respuesta.ok) {
    throw new Error(`${respuesta.status} ${respuesta.statusText}`)
  }

  return respuesta.status === 204 ? null : respuesta.json()
}
