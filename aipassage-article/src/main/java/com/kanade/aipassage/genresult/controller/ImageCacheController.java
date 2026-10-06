package com.kanade.aipassage.genresult.controller;

import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.kanade.aipassage.model.entity.ImageCache;
import com.kanade.aipassage.service.ImageCacheService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 图片缓存表 控制层。
 *
 * @author kanade
 */
@RestController
@RequestMapping("/imageCache")
public class ImageCacheController {

    @Autowired
    private ImageCacheService imageCacheService;

    /**
     * 保存图片缓存表。
     *
     * @param imageCache 图片缓存表
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody ImageCache imageCache) {
        return imageCacheService.save(imageCache);
    }

    /**
     * 根据主键删除图片缓存表。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable String id) {
        return imageCacheService.removeById(id);
    }

    /**
     * 根据主键更新图片缓存表。
     *
     * @param imageCache 图片缓存表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public boolean update(@RequestBody ImageCache imageCache) {
        return imageCacheService.updateById(imageCache);
    }

    /**
     * 查询所有图片缓存表。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<ImageCache> list() {
        return imageCacheService.list();
    }

    /**
     * 根据主键获取图片缓存表。
     *
     * @param id 图片缓存表主键
     * @return 图片缓存表详情
     */
    @GetMapping("getInfo/{id}")
    public ImageCache getInfo(@PathVariable String id) {
        return imageCacheService.getById(id);
    }

    /**
     * 分页查询图片缓存表。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<ImageCache> page(Page<ImageCache> page) {
        return imageCacheService.page(page);
    }

}
