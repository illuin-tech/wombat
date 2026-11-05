import {describe, expect, it} from 'vitest'
import {buildDashboardRequests, buildServiceMetricData, buildTimelineDatasets, buildTimelineLabels, encodeFilters, includedServices, serviceOptions} from '@/pages/impact/model/dashboard'
import {aggregate, type ServiceImpact} from '@/pages/impact/model/aggregate'
import type {AssetInfo} from '@/domain/environment'
import type {AssetImpact, TimelineResponse} from '@/domain/impact'

const assets: AssetInfo[] = [
    {id: 'a', name: 'A', type: 'container'},
    {id: 'b', name: 'B', type: 'container'},
]

describe('dashboard helpers', () => {
    it('builds one sorted global service list from unfiltered impacts', () => {
        const service = (serviceId: string) => ({
            serviceId,
            assetType: {namespace: 'test', localName: 'container', name: 'Container', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER'},
            footprint: {gwp: null, pe: null, adp: null},
            profile: null,
            share: 1,
            location: null,
            serverSpecification: null,
            loadPercent: 0,
            nodeCount: 0,
        })
        const impacts = [
            {assetIdentity: {id: 'a', environmentId: 'env', name: 'A'}, footprint: {gwp: null, pe: null, adp: null}, serviceImpacts: [service('zeta'), service('alpha')], provider: 'BOAVIZTA', range: null},
            {assetIdentity: {id: 'b', environmentId: 'env', name: 'B'}, footprint: {gwp: null, pe: null, adp: null}, serviceImpacts: [service('alpha'), service('beta')], provider: 'BOAVIZTA', range: null},
        ] as AssetImpact[]
        expect(serviceOptions(impacts)).toEqual(['alpha', 'beta', 'zeta'])
    })

    it('resolves route service selections into a global checked list', () => {
        const all = ['alpha', 'beta', 'zeta']
        expect(includedServices(all, new Map())).toEqual(all)
        expect(includedServices(all, new Map([['a', ['zeta']], ['b', ['alpha']]]))).toEqual(['alpha', 'zeta'])
        expect(includedServices(all, new Map([['a', []]]))).toEqual(all)
    })

    it('encodes each selected asset with the global service subset', () => {
        expect(encodeFilters(['a', 'b'], ['alpha', 'zeta'], ['alpha', 'beta', 'zeta'])).toEqual(['a=alpha,zeta', 'b=alpha,zeta'])
        expect(encodeFilters(['a', 'b'], ['alpha', 'beta'], ['alpha', 'beta'])).toEqual(['a', 'b'])
        expect(encodeFilters([], ['alpha'], ['alpha', 'beta'])).toEqual([])
    })

    it('uses every asset when no service selection matches the environment', () => {
        const requests = buildDashboardRequests('env', assets, new Map([['unknown', ['svc']]]), new Date('2025-01-01T00:00:00Z'), new Date('2025-02-01T00:00:00Z'))
        expect(requests.assetIds).toEqual(['a', 'b'])
        expect(requests.impact.environments[0].assets).toEqual([{id: 'a'}, {id: 'b'}])
    })

    it('requests all impact services but applies selected services to the timeline', () => {
        const requests = buildDashboardRequests('env', assets, new Map([['a', ['svc-1']]]), new Date('2025-01-01T00:00:00Z'), new Date('2025-02-01T00:00:00Z'))
        expect(requests.impact.sourceTimeRange).toEqual({start: '2025-01-01T00:00:00Z', end: '2025-02-01T00:00:00Z'})
        expect(requests.impact.environments[0].assets).toEqual([{id: 'a'}])
        expect(requests.timeline.sourceTimeRange).toEqual({start: '2025-01-01T00:00:00Z', end: '2025-02-01T00:00:00Z'})
        expect(requests.timeline.environments[0].assets).toEqual([{id: 'a', serviceIds: ['svc-1']}])
    })

    it('builds timeline labels and embedded/use datasets for the selected metric', () => {
        const timeline: TimelineResponse = {
            stepMs: 3_600_000,
            stepLabel: 'hour',
            stepPlural: 'hours',
            bucketStarts: [Date.parse('2025-01-01T00:00:00Z')],
            series: [{key: 'gwp', label: 'GWP', unit: 'kgCO2eq', buckets: [{embedded: 2, use: 3}]}],
        }
        expect(buildTimelineLabels(timeline)).toEqual(['Jan 1, 00:00'])
        expect(buildTimelineDatasets(timeline, 'gwp')).toEqual([
            {label: 'Embedded', data: [2]},
            {label: 'Use', data: [3]},
        ])
    })

    it('builds separate embedded and use metric datasets from the dashboard model', () => {
        const service: ServiceImpact = {
            assetName: 'A',
            service: 'svc',
            label: 'A / svc',
            assetType: {namespace: 'test', localName: 'container', name: 'Container', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER'},
            footprint: {
                gwp: {unit: 'kgCO2eq', description: 'GWP', embedded: {value: 2}, use: {value: 3}},
                pe: null,
                adp: null,
            },
        }
        const model = {...aggregate([], []), serviceImpacts: [service]}
        expect(buildServiceMetricData(model, 'gwp')).toEqual({
            labels: ['svc'],
            datasets: [{label: 'Embedded', data: [2]}, {label: 'Use', data: [3]}],
        })
    })
})
