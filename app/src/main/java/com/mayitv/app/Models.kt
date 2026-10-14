package com.mayitv.app

data class Vod(val id: String, val title: String, val cover: String, val remark: String)

data class VodDetail(
    val id: String,
    val title: String,
    val cover: String,
    val info: String,
    val desc: String,
    val sources: List<PlaySource>
)

data class PlaySource(val name: String, val episodes: List<Episode>)

data class Episode(val name: String, val playUrl: String)
