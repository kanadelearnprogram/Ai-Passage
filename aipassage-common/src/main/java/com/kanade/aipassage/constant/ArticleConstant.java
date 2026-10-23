package com.kanade.aipassage.constant;

public interface ArticleConstant {
    /**
     * Pexels API 地址
     */
    String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    /**
     * Pexels 每页返回数量
     */
    int PEXELS_PER_PAGE = 1;

    /**
     * Pexels 图片方向：横向
     */
    String PEXELS_ORIENTATION_LANDSCAPE = "landscape";

    String PICSUM_URL_TEMPLATE = "https://picsum.photos/800/600?random=%d";

    long SSE_TIMEOUT_MS = 30 *60 * 1000L;
    long SSE_RECONNECT_TIME_MS = 3000L;

    String BING_IMAGE_SEARCH_URL = "https://cn.bing.com/images/async";

    String EMOJI_PACK_SUFFIX = "熊猫头表情包";
}
