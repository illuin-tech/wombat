/* Port of UIController.parseServices: repeatable `services=assetId=svc1,svc2` query values. */

export type ServicesSelection = Map<string, string[]>

export function parseServices(entries: readonly (string | null | undefined)[] | null | undefined): ServicesSelection {
  const services: ServicesSelection = new Map()
  if (!entries) return services
  for (const entry of entries) {
    if (!entry || entry.trim() === '') continue
    const separator = entry.indexOf('=')
    const assetId = (separator < 0 ? entry : entry.substring(0, separator)).trim()
    if (assetId === '') continue
    const assetServices = separator < 0
      ? []
      : entry.substring(separator + 1).split(',').map(service => service.trim()).filter(service => service !== '')
    services.set(assetId, assetServices)
  }
  return services
}

export function encodeServices(selection: ServicesSelection): string[] {
  return [...selection.entries()].map(([assetId, services]) => services.length === 0 ? assetId : `${assetId}=${services.join(',')}`)
}
