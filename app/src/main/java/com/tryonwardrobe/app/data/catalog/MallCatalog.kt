package com.tryonwardrobe.app.data.catalog

import com.tryonwardrobe.app.data.local.ClothingItem
import java.net.URLEncoder

data class MallBrandItem(
    val id: String,
    val brand: String,
    val name: String,
    val category: String,
    val color: String,
    val fit: String,
    val material: String,
    val styleTags: List<String>,
    val searchKeyword: String,
    val priceHintTwd: String
) {
    fun buildOfficialSearchUrl(): String {
        val encoded = runCatching {
            URLEncoder.encode(searchKeyword, "UTF-8")
        }.getOrDefault(searchKeyword)
        return when (brand) {
            MallCatalog.BRAND_UNIQLO -> "https://www.uniqlo.com/tw/zh_TW/search.html?description=$encoded&q=$encoded"
            MallCatalog.BRAND_GU -> "https://www.gu-global.com/tw/zh_TW/search.html?description=$encoded&q=$encoded"
            MallCatalog.BRAND_MUJI -> "https://www.muji.com/tw/zh_TW/search?q=$encoded"
            else -> "https://www.google.com/search?q=${URLEncoder.encode("$brand $searchKeyword", "UTF-8")}"
        }
    }

    fun toVirtualTryOnSpecification(): String {
        return "[$brand] $name ($category) — Color: $color, Fit: $fit, Material/Texture: $material"
    }
}

object MallCatalog {
    const val BRAND_ALL = "全部品牌"
    const val BRAND_UNIQLO = "UNIQLO"
    const val BRAND_GU = "GU"
    const val BRAND_MUJI = "無印良品 MUJI"

    val BRAND_FILTERS = listOf(BRAND_ALL, BRAND_UNIQLO, BRAND_GU, BRAND_MUJI)

    val EVERGREEN_ITEMS: List<MallBrandItem> = listOf(
        // --- UNIQLO ---
        MallBrandItem(
            id = "UQ-01",
            brand = BRAND_UNIQLO,
            name = "U AIRism 棉質寬版圓領T恤 (五分袖)",
            category = ClothingItem.CAT_TOP,
            color = "奶油白",
            fit = "寬鬆",
            material = "外層挺括天竺棉、內裡涼感絲滑 AIRism 混紡",
            styleTags = listOf("日系簡約", "City Boy 城市機能", "Clean Fit 極簡俐落"),
            searchKeyword = "AIRism 棉質寬版圓領T恤",
            priceHintTwd = "NT$590"
        ),
        MallBrandItem(
            id = "UQ-02",
            brand = BRAND_UNIQLO,
            name = "特級亞麻襯衫 (長袖)",
            category = ClothingItem.CAT_TOP,
            color = "亞麻米杏",
            fit = "合身",
            material = "100% 歐洲天然亞麻，自然透氣微皺肌理",
            styleTags = listOf("度假慵懶", "日系簡約", "商務休閒"),
            searchKeyword = "特級亞麻襯衫",
            priceHintTwd = "NT$990"
        ),
        MallBrandItem(
            id = "UQ-03",
            brand = BRAND_UNIQLO,
            name = "牛津襯衫 (扣領經典款)",
            category = ClothingItem.CAT_TOP,
            color = "清爽水藍",
            fit = "合身",
            material = "雙股紗牛津布，挺括學院質感",
            styleTags = listOf("美式復古 Ivy", "商務休閒", "Clean Fit 極簡俐落"),
            searchKeyword = "牛津襯衫",
            priceHintTwd = "NT$790"
        ),
        MallBrandItem(
            id = "UQ-04",
            brand = BRAND_UNIQLO,
            name = "打褶寬褲 (高腰垂墜西裝料)",
            category = ClothingItem.CAT_BOTTOM,
            color = "炭灰黑",
            fit = "寬鬆",
            material = "雙向彈性垂墜西裝面料，正面單打褶修飾腿型",
            styleTags = listOf("Clean Fit 極簡俐落", "韓系質感", "商務休閒"),
            searchKeyword = "打褶寬褲",
            priceHintTwd = "NT$990"
        ),
        MallBrandItem(
            id = "UQ-05",
            brand = BRAND_UNIQLO,
            name = "寬版直筒牛仔褲",
            category = ClothingItem.CAT_BOTTOM,
            color = "復古水洗藍",
            fit = "寬鬆",
            material = "100% 純棉丹寧布，高腰落地直筒剪裁",
            styleTags = listOf("美式街頭", "韓系質感", "City Boy 城市機能"),
            searchKeyword = "寬版直筒牛仔褲",
            priceHintTwd = "NT$1,290"
        ),
        MallBrandItem(
            id = "UQ-06",
            brand = BRAND_UNIQLO,
            name = "Miracle Air 感動西裝外套",
            category = ClothingItem.CAT_OUTER,
            color = "深海軍藍",
            fit = "合身",
            material = "超輕量高彈力速乾西裝面料，俐落平駁領",
            styleTags = listOf("商務休閒", "約會優雅", "美式復古 Ivy"),
            searchKeyword = "感動西裝外套",
            priceHintTwd = "NT$1,990"
        ),
        MallBrandItem(
            id = "UQ-07",
            brand = BRAND_UNIQLO,
            name = "BLOCKTECH 防風雨連帽外套",
            category = ClothingItem.CAT_OUTER,
            color = "軍墨綠",
            fit = "寬鬆",
            material = "防潑水防風透濕機能塗層，立體連帽剪裁",
            styleTags = listOf("戶外山系 Gorpcore", "City Boy 城市機能", "運動休閒"),
            searchKeyword = "BLOCKTECH 防風雨連帽外套",
            priceHintTwd = "NT$1,990"
        ),
        MallBrandItem(
            id = "UQ-08",
            brand = BRAND_UNIQLO,
            name = "弧形迷你肩背包 (半月包)",
            category = ClothingItem.CAT_BAG,
            color = "象牙白",
            fit = "合身",
            material = "輕量防潑水尼龍霧面質感，貼合身形弧度",
            styleTags = listOf("日系簡約", "運動休閒", "City Boy 城市機能"),
            searchKeyword = "弧形迷你肩背包",
            priceHintTwd = "NT$390"
        ),
        MallBrandItem(
            id = "UQ-09",
            brand = BRAND_UNIQLO,
            name = "抗UV 威靈頓太陽眼鏡",
            category = ClothingItem.CAT_EYEWEAR,
            color = "琥珀玳瑁框茶色鏡片",
            fit = "合身",
            material = "UV400 防護半透明醋酸纖維質感鏡框",
            styleTags = listOf("度假慵懶", "美式復古 Ivy", "韓系質感"),
            searchKeyword = "太陽眼鏡",
            priceHintTwd = "NT$590"
        ),

        // --- GU ---
        MallBrandItem(
            id = "GU-01",
            brand = BRAND_GU,
            name = "Barrel Leg 錐形牛仔褲 (酒桶褲)",
            category = ClothingItem.CAT_BOTTOM,
            color = "復古灰黑丹寧",
            fit = "寬鬆",
            material = "立體膝部車線弧形輪廓丹寧布，褲腳微收錐形",
            styleTags = listOf("韓系質感", "美式街頭", "Clean Fit 極簡俐落"),
            searchKeyword = "Barrel Leg 錐形牛仔褲",
            priceHintTwd = "NT$990"
        ),
        MallBrandItem(
            id = "GU-02",
            brand = BRAND_GU,
            name = "Heavyweight 重磅寬版連帽上衣",
            category = ClothingItem.CAT_TOP,
            color = "麻花淺灰",
            fit = "寬鬆",
            material = "高磅數內刷毛硬挺棉質，立體挺括帽型",
            styleTags = listOf("美式街頭", "City Boy 城市機能", "運動休閒"),
            searchKeyword = "重磅寬版連帽上衣",
            priceHintTwd = "NT$890"
        ),
        MallBrandItem(
            id = "GU-03",
            brand = BRAND_GU,
            name = "透膚開襟針織外套",
            category = ClothingItem.CAT_OUTER,
            color = "霧霾冰藍",
            fit = "合身",
            material = "輕薄微透膚細針織紗線，珍珠母貝小排釦",
            styleTags = listOf("韓系質感", "約會優雅", "度假慵懶"),
            searchKeyword = "開襟針織外套",
            priceHintTwd = "NT$790"
        ),
        MallBrandItem(
            id = "GU-04",
            brand = BRAND_GU,
            name = "超寬版工作傘兵褲 (Parachute Pants)",
            category = ClothingItem.CAT_BOTTOM,
            color = "卡其軍綠",
            fit = "寬鬆",
            material = "輕量防撕裂棉質混紡，膝蓋立體打褶與褲口抽繩",
            styleTags = listOf("戶外山系 Gorpcore", "美式街頭", "City Boy 城市機能"),
            searchKeyword = "超寬版工作褲",
            priceHintTwd = "NT$890"
        ),
        MallBrandItem(
            id = "GU-05",
            brand = BRAND_GU,
            name = "厚底莫卡辛樂福鞋",
            category = ClothingItem.CAT_SHOES,
            color = "亮面深黑",
            fit = "合身",
            material = "合成皮革鞋面搭配鋸齒厚底增高橡膠鞋底",
            styleTags = listOf("韓系質感", "美式復古 Ivy", "Clean Fit 極簡俐落"),
            searchKeyword = "樂福鞋",
            priceHintTwd = "NT$890"
        ),
        MallBrandItem(
            id = "GU-06",
            brand = BRAND_GU,
            name = "尼龍斜背郵差包",
            category = ClothingItem.CAT_BAG,
            color = "曜石黑",
            fit = "寬鬆",
            material = "霧面尼龍大容量郵差包型，寬版可調肩帶",
            styleTags = listOf("City Boy 城市機能", "美式街頭", "戶外山系 Gorpcore"),
            searchKeyword = "肩背包",
            priceHintTwd = "NT$590"
        ),

        // --- 無印良品 MUJI ---
        MallBrandItem(
            id = "MJ-01",
            brand = BRAND_MUJI,
            name = "新疆棉水洗牛津布立領襯衫",
            category = ClothingItem.CAT_TOP,
            color = "原色生成白",
            fit = "合身",
            material = "100% 有機水洗牛津棉，溫潤柔軟立領無領設計",
            styleTags = listOf("日系簡約", "Clean Fit 極簡俐落", "度假慵懶"),
            searchKeyword = "水洗牛津布立領襯衫",
            priceHintTwd = "NT$790"
        ),
        MallBrandItem(
            id = "MJ-02",
            brand = BRAND_MUJI,
            name = "法國亞麻寬版長褲",
            category = ClothingItem.CAT_BOTTOM,
            color = "燕麥自然灰",
            fit = "寬鬆",
            material = "100% 法國水洗亞麻，透氣自然垂墜寬直筒",
            styleTags = listOf("日系簡約", "度假慵懶", "約會優雅"),
            searchKeyword = "亞麻長褲",
            priceHintTwd = "NT$1,390"
        ),
        MallBrandItem(
            id = "MJ-03",
            brand = BRAND_MUJI,
            name = "棉混撥水加工風衣外套",
            category = ClothingItem.CAT_OUTER,
            color = "暖沙卡其",
            fit = "寬鬆",
            material = "有機棉混防潑水挺版斜紋布，經典A字長版風衣輪廓",
            styleTags = listOf("日系簡約", "商務休閒", "約會優雅"),
            searchKeyword = "撥水加工風衣外套",
            priceHintTwd = "NT$2,590"
        ),
        MallBrandItem(
            id = "MJ-04",
            brand = BRAND_MUJI,
            name = "撥水加工有機棉休閒鞋 (小白鞋)",
            category = ClothingItem.CAT_SHOES,
            color = "生成米白",
            fit = "合身",
            material = "防潑水帆布鞋面與支撐足弓人體工學鞋墊",
            styleTags = listOf("日系簡約", "Clean Fit 極簡俐落", "City Boy 城市機能"),
            searchKeyword = "撥水加工有機棉休閒鞋",
            priceHintTwd = "NT$890"
        ),
        MallBrandItem(
            id = "MJ-05",
            brand = BRAND_MUJI,
            name = "UV400 防撥水有機棉漁夫帽",
            category = ClothingItem.CAT_HAT_SCARF,
            color = "深摩卡棕",
            fit = "合身",
            material = "有機棉防潑水挺版帽簷，簡約無印素面設計",
            styleTags = listOf("戶外山系 Gorpcore", "日系簡約", "度假慵懶"),
            searchKeyword = "漁夫帽",
            priceHintTwd = "NT$690"
        )
    )

    fun filterByBrand(brand: String): List<MallBrandItem> {
        return if (brand == BRAND_ALL) EVERGREEN_ITEMS
        else EVERGREEN_ITEMS.filter { it.brand == brand }
    }
}
