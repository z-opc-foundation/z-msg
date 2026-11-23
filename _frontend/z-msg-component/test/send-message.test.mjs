// Mock-only SendMessage regression tests using this package's Vite dependency.
// Hook/Form doubles exercise handlers and state, not browser/Ant Design rendering.
import test, {after} from 'node:test'
import assert from 'node:assert/strict'
import {fileURLToPath} from 'node:url'
import {createServer} from 'vite'

const root = fileURLToPath(new URL('../', import.meta.url))
const context = {current: null}
globalThis.__sendMessageTests = context
after(() => { delete globalThis.__sendMessageTests })

const bridge = 'const context = globalThis.__sendMessageTests;'
const modules = {
    react: `${bridge}
        export const useState = (...args) => context.current.hooks.useState(...args);
        export const useRef = (...args) => context.current.hooks.useRef(...args);
        export const useEffect = (...args) => context.current.hooks.useEffect(...args);
        export const useMemo = (...args) => context.current.hooks.useMemo(...args);
        export const useCallback = (fn, deps) => useMemo(() => fn, deps);`,
    'react/jsx-runtime': `
        export const jsx = (type, props) => ({type, props});
        export const jsxs = jsx;
        export const Fragment = 'Fragment';`,
    'react/jsx-dev-runtime': `
        export const jsxDEV = (type, props) => ({type, props});
        export const Fragment = 'Fragment';`,
    antd: `${bridge}
        const component = (name) => Object.assign(() => null, {displayName: name});
        export const Alert = component('Alert'), Button = component('Button'), Card = component('Card'),
            Col = component('Col'), Row = component('Row'), Select = component('Select'),
            Space = component('Space'), Tag = component('Tag');
        export const Typography = {Text: component('Text')};
        export const Input = Object.assign(component('Input'), {TextArea: component('Input.TextArea')});
        export const Form = Object.assign(component('Form'), {
            Item: component('Form.Item'),
            useForm: () => [context.current.form],
            useWatch: (name, form) => form.values[name],
        });
        export const message = {
            success: (text) => context.current.messages.push(['success', text]),
            error: (text) => context.current.messages.push(['error', text]),
        };`,
    '@ant-design/icons': `export const SendOutlined = 'SendOutlined';`,
    '../services/api': `${bridge}
        export const msgApi = {
            channels: (...args) => context.current.api.channels(...args),
            send: (...args) => context.current.api.send(...args),
        };`,
}
const server = await createServer({
    root,
    configFile: false,
    logLevel: 'silent',
    appType: 'custom',
    server: {middlewareMode: true, hmr: false, watch: null},
    optimizeDeps: {noDiscovery: true, include: []},
    ssr: {noExternal: true},
    esbuild: {jsx: 'automatic'},
    plugins: [{
        name: 'mock-page-imports',
        enforce: 'pre',
        resolveId(id) { if (Object.hasOwn(modules, id)) return `\0mock:${id}` },
        load(id) { if (id.startsWith('\0mock:')) return modules[id.slice(6)] },
    }],
})
let SendMessage
try {
    SendMessage = (await server.ssrLoadModule('/src/pages/SendMessage.jsx')).default
} finally {
    await server.close()
}

const deferred = () => {
    let resolve, reject
    const promise = new Promise((yes, no) => { resolve = yes; reject = no })
    return {promise, resolve, reject}
}
const flush = () => new Promise((resolve) => setImmediate(resolve))
const sameDeps = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]))

function elements(value, result = []) {
    if (Array.isArray(value)) value.forEach((item) => elements(item, result))
    else if (value && typeof value === 'object' && value.type && value.props) {
        result.push(value)
        Object.values(value.props).forEach((item) => elements(item, result))
    }
    return result
}
const typeOf = (node) => node.type.displayName || node.type

function mount(Component, {values = {}, api = {}} = {}) {
    const slots = []
    let cursor = 0, effects = []
    const state = {
        api: {
            channels: async () => [],
            send: async () => { throw new Error('Unexpected send') },
            ...api,
        },
        messages: [],
        form: {
            values: {...values}, initialValues: {}, resets: 0, validations: 0,
            async validateFields() { this.validations++; return {...this.values} },
            resetFields() { this.resets++; this.values = {...this.initialValues} },
        },
        hooks: {
            useState(initial) {
                const slot = cursor++
                slots[slot] ??= {value: typeof initial === 'function' ? initial() : initial}
                return [slots[slot].value, (value) => {
                    slots[slot].value = typeof value === 'function' ? value(slots[slot].value) : value
                }]
            },
            useRef(initial) {
                const slot = cursor++
                slots[slot] ??= {current: initial}
                return slots[slot]
            },
            useMemo(factory, deps) {
                const slot = cursor++
                if (!sameDeps(slots[slot]?.deps, deps)) slots[slot] = {value: factory(), deps}
                return slots[slot].value
            },
            useEffect(effect, deps) {
                const slot = cursor++
                if (!sameDeps(slots[slot]?.deps, deps)) {
                    const old = slots[slot]
                    slots[slot] = {deps}
                    effects.push(() => {
                        old?.cleanup?.()
                        slots[slot].cleanup = effect()
                    })
                }
            },
        },
    }
    const page = {
        ...state,
        render() {
            context.current = state
            cursor = 0
            const tree = Component()
            const form = elements(tree).find((node) => typeOf(node) === 'Form')
            if (form?.props.initialValues) state.form.initialValues = form.props.initialValues
            const pending = effects
            effects = []
            pending.forEach((effect) => effect())
            return tree
        },
        get(type, predicate = () => true) {
            const node = elements(this.render()).find((item) => typeOf(item) === type && predicate(item.props))
            assert.ok(node, `Missing ${type}`)
            return node.props
        },
        has(type) { return elements(this.render()).some((item) => typeOf(item) === type) },
        unmount() { slots.forEach((slot) => slot?.cleanup?.()) },
    }
    page.render()
    return page
}

// Deliberately invoke old handlers before a render to expose state-only locks.
test('EMAIL success resets the watched channel; next IN_APP payload uses submitted values', async () => {
    const payloads = []
    const page = mount(SendMessage, {
        values: {channel: 'EMAIL', receiver: 'mock@example.test', content: 'mock email'},
        api: {send: async (payload) => { payloads.push(payload); return {msgId: 'mock'} }},
    })
    const submit = page.get('Form').onFinish
    assert.equal(page.get('Form.Item', (props) => props.name === 'receiver').label, '收件地址')
    await submit()
    assert.equal(payloads[0].receiver, 'mock@example.test')
    assert.equal(Object.hasOwn(payloads[0], 'user_id'), false)
    assert.equal(page.form.values.channel, 'IN_APP')
    const receiver = page.get('Form.Item', (props) => props.name === 'receiver')
    assert.equal(receiver.label, '收件人用户 ID')
    assert.ok(receiver.rules.some((rule) => rule.pattern?.test('1001')))
    page.form.values = {...page.form.values, receiver: '1001', content: 'mock in-app'}
    await submit()
    assert.equal(payloads[1].channel, 'IN_APP')
    assert.equal(payloads[1].user_id, 1001)
    assert.equal(Object.hasOwn(payloads[1], 'receiver'), false)
    assert.equal(page.form.resets, 2)
    assert.doesNotMatch(JSON.stringify(page.render()), /\/api\/msg\/send/)
    page.unmount()
})

test('send locks validation and request windows, preserves failed inputs and permits retry', async () => {
    const validation = deferred(), request = deferred()
    let calls = 0
    const values = {channel: 'EMAIL', receiver: 'mock@example.test', content: 'keep me'}
    const page = mount(SendMessage, {values, api: {send: () => { calls++; return request.promise }}})
    page.form.validateFields = () => { page.form.validations++; return validation.promise }
    const submit = page.get('Form').onFinish
    const first = submit()
    await submit()
    assert.equal(page.form.validations, 1)
    assert.equal(calls, 0)
    assert.equal(page.get('Form').disabled, true)
    assert.equal(page.get('Button', (props) => props.htmlType === 'submit').loading, true)
    validation.resolve(values)
    await flush()
    await submit()
    assert.equal(calls, 1)
    request.reject(new Error('mock send failed'))
    await first
    assert.deepEqual(page.form.values, values)
    assert.equal(page.form.resets, 0)
    assert.equal(page.get('Form').disabled, false)
    assert.equal(page.get('Alert', (props) => props.type === 'error').description, 'mock send failed')
    page.api.send = async () => ({msgId: 'retry'})
    await submit()
    assert.equal(page.form.resets, 1)
    page.unmount()
})

test('channel load failures block sending until a successful retry', async () => {
    let writes = 0
    const page = mount(SendMessage, {api: {
        channels: async () => { throw new Error('channel unavailable') },
        send: async () => { writes++; return {msgId: 'unexpected'} },
    }})
    await flush()
    assert.equal(page.get('Alert').title, '通道加载失败')
    assert.equal(page.get('Button', props => props.htmlType === 'submit').disabled, true)
    await page.get('Form').onFinish()
    assert.equal(writes, 0)
    page.api.channels = async () => [{channel: 'IN_APP', real: true}]
    await page.get('Button', props => props.children === '重试').onClick()
    assert.equal(page.has('Alert'), false)
    assert.equal(page.get('Button', props => props.htmlType === 'submit').disabled, false)
    page.unmount()
})

test('malformed channels are not silently treated as an empty list', async () => {
    const page = mount(SendMessage, {api: {channels: async () => ({unexpected: []})}})
    await flush()
    assert.equal(page.get('Alert').description, '通道列表响应格式错误')
    assert.equal(page.get('Button', props => props.htmlType === 'submit').disabled, true)
    page.unmount()
})

test('send validation failure releases the lock without a write or input reset', async () => {
    let writes = 0
    const values = {content: 'keep me'}
    const page = mount(SendMessage, {values, api: {
        send: async () => { writes++; return {msgId: 'retry'} },
    }})
    page.form.validateFields = async () => {
        page.form.validations++
        throw {errorFields: [{name: 'required'}]}
    }
    const submit = page.get('Form').onFinish
    await submit()
    assert.equal(page.get('Form').disabled, false)
    assert.equal(page.get('Button', (props) => props.htmlType === 'submit').loading, false)
    assert.equal(writes, 0)
    assert.equal(page.form.resets, 0)
    assert.deepEqual(page.form.values, values)
    assert.equal(page.messages.length, 0)
    assert.equal(page.has('Alert'), false)
    // A valid retry also proves that the synchronous lock was released.
    page.form.values = {channel: 'IN_APP', receiver: '1001', content: 'keep me'}
    page.form.validateFields = async () => { page.form.validations++; return {...page.form.values} }
    await submit()
    assert.equal(page.form.validations, 2)
    assert.equal(writes, 1)
    assert.equal(page.form.resets, 1)
    await flush()
    page.unmount()
})
