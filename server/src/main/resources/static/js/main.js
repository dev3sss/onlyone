const { createApp, ref } = Vue
const { MessagePlugin } = TDesign
let app = createApp({
    components: {
        layoutHeader,
        layoutSideNav,
        layoutContent
    }
})
app.use(TDesign).mount('#app')