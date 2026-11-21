package com.retro.iptv.remote

import org.json.JSONObject

object RemoteProtocol {
    const val SERVICE_TYPE = "_retrotv._tcp."
    const val SERVICE_NAME = "RETROTV"
    const val CMD_PAIR = "PAIR"
    const val CMD_DPAD = "DPAD"
    const val CMD_NUMBER = "NUMBER"
    const val CMD_CHANNEL_ID = "CHANNEL_ID"
    const val CMD_SEARCH = "SEARCH"
    const val CMD_MEDIA = "MEDIA"
    const val CMD_SYNC_REQ = "SYNC_REQ"
    const val CMD_LAUNCH_RETROTV = "LAUNCH_RETROTV"

    const val TARGET_HOME = "HOME"
    const val TARGET_SCORE_CARD = "SCORE_CARD"
    const val TARGET_NEWS_TICKER = "NEWS_TICKER"
    const val TARGET_FM_RADIO = "FM_RADIO"
    const val TARGET_YOUTUBE = "YOUTUBE"
    val launchTargets = setOf(TARGET_HOME, TARGET_SCORE_CARD, TARGET_NEWS_TICKER, TARGET_FM_RADIO, TARGET_YOUTUBE)

    const val EVT_PAIRED = "PAIRED"
    const val EVT_STATE = "STATE"

    fun createCommand(cmd: String, value: String = ""): String =
        JSONObject().put("cmd", cmd).put("value", value).toString()
}
