import {aggregate, type DashboardModel} from '@/pages/impact/model/aggregate'
import type {AssetInfo} from '@/domain/environment'
import type {AssetImpact, ImpactRequest, TimelineResponse} from '@/domain/impact'
import type {ServicesSelection} from '@/pages/impact/model/servicesQuery'
import {asIsoUtc} from '@/services/format/formatters'

export interface DashboardRequests {
    impact: ImpactRequest
    timeline: ImpactRequest
    assetIds: string[]
}

/** Distinct service IDs, independent of asset, in stable display order. */
export function serviceOptions(impacts: AssetImpact[]): string[] {
    return [...new Set(impacts.flatMap(impact => impact.serviceImpacts.map(service => service.serviceId)))].sort()
}

/** Resolves the route's per-asset query representation to the global picker selection. */
export function includedServices(all: string[], selection: ServicesSelection): string[] {
    if (selection.size === 0 || [...selection.values()].some(services => services.length === 0))
        return [...all]
    const requested = new Set([...selection.values()].flat())
    return all.filter(service => requested.has(service))
}

/** Encodes the global service choice once for each selected asset, matching impact.js. */
export function encodeFilters(assetIds: readonly string[], selectedServices: readonly string[], all: readonly string[]): string[] {
    const everyServiceSelected = selectedServices.length === all.length && all.every(service => selectedServices.includes(service))
    const suffix = selectedServices.length > 0 && !everyServiceSelected ? `=${selectedServices.join(',')}` : ''
    return assetIds.map(assetId => `${assetId}${suffix}`)
}

export function buildDashboardRequests(
    environmentId: string,
    assets: AssetInfo[],
    selection: ServicesSelection,
    start: Date,
    end: Date,
): DashboardRequests {
    const knownIds = new Set(assets.map(asset => asset.id))
    const selectedIds = [...selection.keys()].filter(id => knownIds.has(id))
    const assetIds = selectedIds.length > 0 ? selectedIds : assets.map(asset => asset.id)
    const range = {start: asIsoUtc(start), end: asIsoUtc(end)}
    const impactAssets = assetIds.map(id => ({id}))
    const timelineAssets = assetIds.map((id) => {
        const services = selection.get(id)
        return services && services.length > 0 ? {id, serviceIds: services} : {id}
    })

    return {
        assetIds,
        impact: {sourceTimeRange: range, environments: [{id: environmentId, assets: impactAssets}]},
        timeline: {sourceTimeRange: range, environments: [{id: environmentId, assets: timelineAssets}]},
    }
}

export function aggregateDashboard(
    impacts: AssetImpact[],
    assets: AssetInfo[],
    selection: ServicesSelection,
): DashboardModel {
    const assetIds = new Set(assets.map(asset => asset.id))
    const selectedIds = [...selection.keys()].filter(id => assetIds.has(id))
    if (selectedIds.length === 0) return aggregate(impacts, assets)
    const filtered = impacts
        .filter(impact => selectedIds.includes(impact.assetIdentity.id))
        .map((impact) => {
            const selectedServices = selection.get(impact.assetIdentity.id) ?? []
            return selectedServices.length === 0
                ? impact
                : {...impact, serviceImpacts: impact.serviceImpacts.filter(service => selectedServices.includes(service.serviceId))}
        })
    return aggregate(filtered, assets)
}

export function buildTimelineDatasets(timeline: TimelineResponse, metric: TimelineResponse['series'][number]['key']) {
    const series = timeline.series.find(candidate => candidate.key === metric)
    if (!series) return []
    return [
        {label: 'Embedded', data: series.buckets.map(bucket => bucket.embedded)},
        {label: 'Use', data: series.buckets.map(bucket => bucket.use)},
    ]
}

export function buildServiceMetricData(model: DashboardModel, metric: 'gwp' | 'pe' | 'adp') {
    return {
        labels: model.serviceImpacts.map(service => service.service),
        datasets: [
            {label: 'Embedded', data: model.serviceImpacts.map(service => service.footprint[metric]?.embedded.value ?? 0)},
            {label: 'Use', data: model.serviceImpacts.map(service => service.footprint[metric]?.use.value ?? 0)},
        ],
    }
}

export function buildTimelineLabels(timeline: TimelineResponse): string[] {
    const hourly = timeline.stepMs < 86_400_000
    const formatter = new Intl.DateTimeFormat('en-US', hourly
        ? {timeZone: 'UTC', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'}
        : {timeZone: 'UTC', month: 'short', day: 'numeric'})
    return timeline.bucketStarts.map((start) => {
        return formatter.format(new Date(start))
    })
}
