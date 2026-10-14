package com.mayitv.app

object SiteConfig {
    const val SITE = "https://www.91mayitv.com"

    // 默认解析地址（暴风资源/红牛资源均使用此解析）
    const val PARSE_API = "https://zj.sp-flv.com:8443?url="

    // 使用桌面端 UA，让站点渲染完整布局（带分类网格，便于遥控器聚焦）
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
