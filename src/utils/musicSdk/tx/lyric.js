import { httpFetch } from '../../request'
import { b64DecodeUnicode, decodeName } from '../../index'

const TX_MUSIC_U_FCG = 'https://u.y.qq.com/cgi-bin/musicu.fcg'

const getLyricNew = (songmid) => {
  const payload = {
    comm: { ct: 24, cv: 1800 },
    req_0: {
      module: 'music.musichallSong.PlayLyricInfo',
      method: 'GetPlayLyricInfo',
      param: {
        crypt: 0,
        lrc_t: 0,
        qrc: 0,
        qrc_t: 0,
        roma: 0,
        roma_t: 0,
        trans: 1,
        trans_t: 0,
        type: 1,
        songMid: songmid,
      },
    },
  }

  const requestObj = httpFetch(TX_MUSIC_U_FCG, {
    method: 'post',
    headers: {
      'User-Agent': 'QQMusic 14090508(android 12)',
      Referer: 'https://y.qq.com/',
    },
    body: payload,
  })

  requestObj.promise = requestObj.promise.then(({ body }) => {
    const data = body?.req_0?.data
    if (!data || !data.lyric) return Promise.reject(new Error('Get lyric failed'))

    return {
      lyric: decodeName(b64DecodeUnicode(data.lyric)),
      tlyric: decodeName(b64DecodeUnicode(data.trans)),
      rlyric: '',
      lxlyric: '',
    }
  })

  return requestObj
}

const getLyricOld = (songmid) => {
  const requestObj = httpFetch(
    `https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${songmid}&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq`,
    {
      headers: {
        Referer: 'https://y.qq.com/portal/player.html',
      },
    }
  )

  requestObj.promise = requestObj.promise.then(({ body }) => {
    if (body.code != 0 || !body.lyric) return Promise.reject(new Error('Get lyric failed'))
    return {
      lyric: decodeName(b64DecodeUnicode(body.lyric)),
      tlyric: decodeName(b64DecodeUnicode(body.trans)),
      rlyric: '',
      lxlyric: '',
    }
  })

  return requestObj
}

const isValidLyric = (result) => {
  return result && typeof result.lyric === 'string' && result.lyric.trim().length > 0
}

export default {
  regexps: {
    matchLrc: /.+"lyric":"([\w=+/]*)".+/,
  },
  getLyric(songmid) {
    const requestObj = { cancelHttp: null }

    const tryNew = getLyricNew(songmid)
    const tryOld = getLyricOld(songmid)

    requestObj.cancelHttp = () => {
      tryNew.cancelHttp()
      tryOld.cancelHttp()
    }

    requestObj.promise = new Promise((resolve, reject) => {
      let newResult = null
      let oldResult = null
      let newFailed = false
      let oldFailed = false

      const checkDone = () => {
        if (newFailed && oldFailed) {
          reject(new Error('Get lyric failed'))
          return
        }
        if (newResult && isValidLyric(newResult)) {
          resolve(newResult)
        } else if (oldResult && isValidLyric(oldResult)) {
          resolve(oldResult)
        }
      }

      tryNew.promise
        .then((result) => {
          newResult = result
          if (isValidLyric(result)) {
            tryOld.cancelHttp()
            resolve(result)
          } else {
            checkDone()
          }
        })
        .catch(() => {
          newFailed = true
          checkDone()
        })

      tryOld.promise
        .then((result) => {
          oldResult = result
          checkDone()
        })
        .catch(() => {
          oldFailed = true
          checkDone()
        })
    })

    return requestObj
  },
}
