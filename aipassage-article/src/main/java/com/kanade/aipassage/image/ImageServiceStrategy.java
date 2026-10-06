package com.kanade.aipassage.image;

import cn.hutool.crypto.digest.DigestUtil;
import com.kanade.aipassage.mapper.ImageCacheMapper;
import com.kanade.aipassage.model.dto.ImageData;
import com.kanade.aipassage.model.dto.ImageRequest;
import com.kanade.aipassage.model.entity.ImageCache;
import com.kanade.aipassage.model.enums.ImageMethodEnum;
import com.kanade.aipassage.cos.CosService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ImageServiceStrategy {
    private static final String CACHE_PREFIX = "image:cache";
    @Resource
    private List<ImageSearchService> imageSearchServices;

    @Resource
    private CosService cosService;

    @Resource
    private ImageCacheMapper imageCacheMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate; // 新增注入

    private final Map<ImageMethodEnum,ImageSearchService> serviceMap = new EnumMap<>(ImageMethodEnum.class);

    @PostConstruct
    public void init(){
        for (ImageSearchService imageSearchService : imageSearchServices) {
            ImageMethodEnum method = imageSearchService.getMethod();
            serviceMap.put(method,imageSearchService);
            log.info("注册图片服务: {} -> {} (AI生图: {}, 降级: {})",
                    method.getValue(),
                    imageSearchService.getClass().getSimpleName(),
                    method.isAiGenerated(),
                    method.isFallback());
        }
    }

    // 获取图片 上传
    public ImageResult getImageAndUpload(String imageSource, ImageRequest request) {
        ImageMethodEnum method = resolveMethod(imageSource);
        ImageSearchService service = serviceMap.get(method);

        if (service == null || !service.isAvailable()) {
            log.warn("图片服务不可用: {}, 尝试降级", method);
            return handleFallbackWithUpload(request.getPosition());
        }
        ImageResult imageByCache = getImageByCache(imageSource, request);

        if (imageByCache != null){
            return imageByCache;
        }

        try {
            // 1. 获取图片数据
            ImageData imageData = service.getImageData(request);

            if (imageData == null || !imageData.isValid()) {
                log.warn("图片数据获取失败, 使用降级方案, method={}", method);
                return handleFallbackWithUpload(request.getPosition());
            }

            // 2. 上传到 COS
            String folder = getFolderForMethod(method);
            String cosUrl = cosService.uploadImageData(imageData, folder);
            saveToCache(imageSource, request, cosUrl, method);
            if (cosUrl != null && !cosUrl.isEmpty()) {
                log.info("图片获取并上传成功, method={}, cosUrl={}", method, cosUrl);
                return new ImageResult(cosUrl, method);
            } else {
                log.warn("图片上传 COS 失败, 使用降级方案, method={}", method);
                return handleFallbackWithUpload(request.getPosition());
            }
        } catch (Exception e) {
            log.error("获取图片并上传异常, method={}", method, e);
            return handleFallbackWithUpload(request.getPosition());
        }
    }

    @Deprecated
    public ImageResult getImage(String imageSource, ImageRequest request) {
        ImageMethodEnum method = resolveMethod(imageSource);
        ImageSearchService service = serviceMap.get(method);

        if (service == null || !service.isAvailable()) {
            log.warn("图片服务不可用: {}, 尝试降级", method);
            return handleFallback(request.getPosition());
        }

        String imageUrl = service.getImage(request);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            return new ImageResult(imageUrl, method);
        } else {
            log.warn("图片获取失败, 使用降级方案, method={}", method);
            return handleFallback(request.getPosition());
        }
    }

    @Deprecated
    public ImageResult getImage(String imageSource, String keywords, String prompt) {
        ImageRequest request = ImageRequest.builder()
                .keywords(keywords)
                .prompt(prompt)
                .build();
        return getImage(imageSource, request);
    }


    private ImageMethodEnum resolveMethod(String imageSource) {
        ImageMethodEnum method = ImageMethodEnum.getByValue(imageSource);
        if (method == null) {
            log.warn("未知的图片来源: {}, 默认使用 {}", imageSource, ImageMethodEnum.getDefaultSearchMethod());
            return ImageMethodEnum.getDefaultSearchMethod();
        }
        return method;
    }

    private ImageResult handleFallback(Integer position) {
        int pos = position != null ? position : 1;
        String fallbackUrl = getFallbackImage(pos);
        return new ImageResult(fallbackUrl, ImageMethodEnum.getFallbackMethod());
    }

    private ImageResult handleFallbackWithUpload(Integer position) {
        int pos = position != null ? position : 1;
        String fallbackUrl = getFallbackImage(pos);

        // 将降级图片也上传到 COS
        ImageData fallbackData = ImageData.fromUrl(fallbackUrl);
        String cosUrl = cosService.uploadImageData(fallbackData, "fallback");

        // 如果上传失败，直接使用原始 URL
        String finalUrl = (cosUrl != null && !cosUrl.isEmpty()) ? cosUrl : fallbackUrl;
        return new ImageResult(finalUrl, ImageMethodEnum.getFallbackMethod());
    }

    public ImageSearchService getService(ImageMethodEnum method) {
        return serviceMap.get(method);
    }

    public List<ImageMethodEnum> getRegisteredMethods() {
        return List.copyOf(serviceMap.keySet());
    }

    public ImageResult getImageByCache(String imageSource, ImageRequest request){
        ImageMethodEnum method = resolveMethod(imageSource);
        // 使用细粒度缓存键，包含文章标题、章节标题等上下文信息
        String cacheKey = generateCacheKey(imageSource, request, method);

        String redisKey = CACHE_PREFIX + cacheKey;
        try {
            String cosUrl = (String) redisTemplate.opsForValue().get(redisKey);
            if (cosUrl != null && !cosUrl.isEmpty()) {
                log.debug("L1 缓存命中(Redis): key={}", cacheKey);
                asyncUpdateHitCount(cacheKey);
                return new ImageResult(cosUrl, method);
            }
        } catch (Exception e) {
            log.warn("L1 缓存(Redis)查询异常，降级到 L2: key={}", cacheKey, e);
        }

        try {
            ImageCache cached = imageCacheMapper.selectOneByQuery(
                    QueryWrapper.create().eq(ImageCache::getCacheKey, cacheKey)
            );
            if (cached != null) {
                log.info("L2 缓存命中(MySQL): key={}, hitCount={}", cacheKey, cached.getHitCount());
                asyncUpdateHitCount(cacheKey);
                return new ImageResult(cached.getCosUrl(), method);
            }
        } catch (Exception e) {
            log.error("L2 缓存(MySQL)查询异常: key={}", cacheKey, e);
        }

        return null;
    }

    private void asyncUpdateHitCount(String cacheKey) {
        CompletableFuture.runAsync(() -> {
            try {
                UpdateChain.of(ImageCache.class)
                        .setRaw(ImageCache::getHitCount,"hitCount + 1")
                        .where(ImageCache::getCacheKey).eq(cacheKey)
                        .update();
            } catch (Exception e) {
                log.warn("缓存命中次数更新失败: key={}", cacheKey, e);
            }
        });
    }
    private void saveToCache(String imageSource, ImageRequest request, String cosUrl, ImageMethodEnum method) {
        // 使用细粒度缓存键
        String cacheKey = generateCacheKey(imageSource, request, method);
        String redisKey = CACHE_PREFIX + cacheKey;

        // 1. 存入 MySQL (持久化)
        ImageCache cache = new ImageCache();
        cache.setCacheKey(cacheKey);
        cache.setSourceType(imageSource);
        cache.setKeywords(request.getKeywords());
        cache.setPrompt(request.getPrompt());
        cache.setCosUrl(cosUrl);
        cache.setIsFallback(false);
        cache.setHitCount(0);
        imageCacheMapper.insert(cache);

        // 2. 存入 Redis (加速)
        try {
            redisTemplate.opsForValue().set(redisKey, cosUrl, 3, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 写入失败，但 MySQL 已保存", e);
        }
    }
    /**
     * 生成细粒度的缓存键
     * 包含文章标题、章节标题、位置等上下文信息，避免不同文章共用同一张配图
     * 
     * @param sourceType 图片来源类型（如 pexels, nano-banana）
     * @param request 图片请求对象
     * @param method 图片生成方式枚举
     * @return MD5 缓存键
     */
    private String generateCacheKey(String sourceType, ImageRequest request, ImageMethodEnum method) {
        StringBuilder raw = new StringBuilder();
        raw.append(sourceType).append(":");
        
        // 添加文章标题（标准化处理）
        if (request.getArticleTitle() != null && !request.getArticleTitle().isEmpty()) {
            raw.append(normalizeText(request.getArticleTitle())).append(":");
        }
        
        // 添加章节标题
        if (request.getSectionTitle() != null && !request.getSectionTitle().isEmpty()) {
            raw.append(normalizeText(request.getSectionTitle())).append(":");
        }
        
        // 添加关键词或提示词
        String effectiveParam = getEffectiveParam(request, method);
        raw.append(effectiveParam != null ? effectiveParam.trim() : "").append(":");
        
        // 添加位置信息（封面图、正文配图等）
        if (request.getPosition() != null) {
            raw.append("pos").append(request.getPosition());
        }
        
        return DigestUtil.md5Hex(raw.toString());
    }

    /**
     * 标准化文本：去除特殊字符，转为小写，限制长度
     * 用于生成更紧凑的缓存键
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 去除标点符号、空格，转为小写
        String normalized = text.replaceAll("[\\p{P}\\s]", "")
                                .toLowerCase();
        // 限制长度（使用处理后的长度，避免越界）
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }
        return normalized;
    }
    private String getEffectiveParam(ImageRequest request, ImageMethodEnum method) {
        // AI 生成类（如 Mermaid）主要依赖 prompt，检索类主要依赖 keywords
        if (method.isAiGenerated()) {
            return request.getPrompt() != null ? request.getPrompt() : "";
        }
        return request.getKeywords() != null ? request.getKeywords() : "";
    }
    public String getFallbackImage(int position) {
        // 优先使用已注册服务的降级方案
        ImageSearchService defaultService = serviceMap.get(ImageMethodEnum.getDefaultSearchMethod());
        if (defaultService != null) {
            return defaultService.getFallbackImage(position);
        }
        return String.format("https://picsum.photos/800/600?random=%d", position);
    }
    private String getFolderForMethod(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "pexels";
            case NANO_BANANA -> "nano-banana";
            case MERMAID -> "mermaid";
            case ICONIFY -> "iconify";
            case EMOJI_PACK -> "emoji-pack";
            case SVG_DIAGRAM -> "svg-diagram";
            case PICSUM -> "picsum";
        };
    }

    // cos图片保存结果
    public static class ImageResult {
        private final String url;
        private final ImageMethodEnum method;

        public ImageResult(String url, ImageMethodEnum method) {
            this.url = url;
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public ImageMethodEnum getMethod() {
            return method;
        }

        public boolean isSuccess() {
            return url != null && !url.isEmpty();
        }
    }
}
