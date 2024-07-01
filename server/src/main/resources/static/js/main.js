const { createApp, ref } = Vue
const { MessagePlugin } = TDesign
let app = createApp({
    components: {
        layoutHeader,
        layoutSideNav,
        layoutContent,
        login
    }
})
app.use(TDesign).mount('#app')

const _token = getCookieValue('token')
if  (window.location.pathname !== '/login' && (_token === undefined || _token === '')) {
    window.location.href = '/login';
}