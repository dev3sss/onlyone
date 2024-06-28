const {reactive} = Vue

// 存在token时，在请求头加上token
axios.interceptors.request.use(
    config => {
        const token = getCookieValue("token")
        token && (config.headers.token = token)
        return config
    }
)

function getCookieObj() {
    if (document.cookie.length === 0) {
        return {}
    }
    let obJson = document.cookie.split("=")[1];
    return JSON.parse(obJson)
}

function removeCookie(key) {
    let obj = getCookieObj()
    Reflect.deleteProperty(obj, key)
    document.cookie = "oo=" + JSON.stringify(obj)
}

function addCookie(key, value) {
    let obj = getCookieObj()
    Reflect.set(obj, key, value)
    document.cookie = "oo=" + JSON.stringify(obj)
}

function getCookieValue(key) {
    let obj = getCookieObj()
    return Reflect.get(obj, key)
}

/**
 * 将字符串格式日期转换为Y-M-D格式
 * @param {String} strDate
 * @param {number} type 1:Y-M-D  else: Y-M-D H:Min:S
 * @returns
 */
function strDateToYMD(strDate, type = 1) {
    let curDate = new Date(strDate)
    let Y = curDate.getFullYear();
    let M = curDate.getMonth() + 1 < 10 ? `0${curDate.getMonth() + 1}` : `${curDate.getMonth() + 1}`;
    let D = curDate.getDate() < 10 ? `0${curDate.getDate()}` : `${curDate.getDate()}`;
    let H = curDate.getHours() < 10 ? `0${curDate.getHours()}` : `${curDate.getHours()}`;
    let Min = curDate.getMinutes() < 10 ? `0${curDate.getMinutes()}` : `${curDate.getMinutes()}`;
    let S = curDate.getSeconds() < 10 ? `0${curDate.getSeconds()}` : `${curDate.getSeconds()}`;
    if (type === 1) {
        return `${Y}-${M}-${D}`
    }
    return `${Y}-${M}-${D} ${H}:${Min}:${S}`
}

function uploadFile(url, formdata, uploadProgress) {
    return new Promise((resolve, reject) => {
        const options = ({
            url: url,
            method: 'post',
            data: formdata,
            onUploadProgress: uploadProgress,
            headers: {
                'Content-Type': 'multipart-formData'
            }
        });
        axios.request(options).then(res => {
            resolve(res)
        }).catch(err => {
            reject(err)
        })
    })
}

function htmlDecode(input) {
    var e = document.createElement('div')
    e.innerHTML = input;
    return e.childNodes.length === 0 ? '' : e.childNodes[0].nodeValue;
}