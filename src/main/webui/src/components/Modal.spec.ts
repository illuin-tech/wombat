import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import Modal from './Modal.vue'

let wrapper: VueWrapper

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
})

function zIndexOf(title: string): number {
  const overlay = [...document.querySelectorAll<HTMLElement>('.asset-modal-overlay')]
    .find(candidate => candidate.querySelector('.asset-modal-title')?.textContent === title)!
  return Number(overlay.style.zIndex)
}

describe('Modal', () => {
  it('shows the modal opened last above the others, whatever their order in the page', async () => {
    const confirmOpen = ref(false)
    const editorOpen = ref(false)
    /* The confirmation is mounted first, as a page's own dialog is before a modal it shows once loaded */
    wrapper = mount(defineComponent({
      components: { Modal },
      setup: () => ({ confirmOpen, editorOpen }),
      template: '<Modal :open="confirmOpen" title="Confirm" /><Modal :open="editorOpen" title="Editor" />',
    }), { attachTo: document.body })

    editorOpen.value = true
    await nextTick()
    confirmOpen.value = true
    await nextTick()

    expect(zIndexOf('Confirm')).toBeGreaterThan(zIndexOf('Editor'))
  })
})
