package com.kanade.aipassage.service.impl;

import com.kanade.aipassage.mapper.ImageCacheMapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.kanade.aipassage.model.entity.ImageCache;
import com.kanade.aipassage.service.ImageCacheService;
import org.springframework.stereotype.Service;

/**
 * 图片缓存表 服务层实现。
 *
 * @author kanade
 */
@Service
public class ImageCacheServiceImpl extends ServiceImpl<ImageCacheMapper, ImageCache>  implements ImageCacheService{

}
