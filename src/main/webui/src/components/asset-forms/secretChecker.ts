import type { InjectionKey } from 'vue'

/** Tells whether a secret key resolves; provided by the page, so that the form does not depend on the API. */
export type SecretChecker = (key: string) => Promise<boolean>

export const SECRET_CHECKER_KEY: InjectionKey<SecretChecker> = Symbol('secretChecker')
