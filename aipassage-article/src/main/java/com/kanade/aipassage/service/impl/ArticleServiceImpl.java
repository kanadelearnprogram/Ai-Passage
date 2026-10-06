package com.kanade.aipassage.service.impl;

import cn.hutool.core.util.IdUtil;
import com.google.gson.reflect.TypeToken;
import com.kanade.aipassage.exception.BusinessException;
import com.kanade.aipassage.exception.ErrorCode;
import com.kanade.aipassage.exception.ThrowUtils;
import com.kanade.aipassage.model.dto.ArticleQueryRequest;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.entity.User;
import com.kanade.aipassage.model.enums.ArticlePhaseEnum;
import com.kanade.aipassage.model.enums.ArticleStatusEnum;
import com.kanade.aipassage.model.enums.ImageMethodEnum;
import com.kanade.aipassage.model.vo.ArticleVO;
import com.kanade.aipassage.service.ArticleAgentService;
import com.kanade.aipassage.utils.GsonUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.kanade.aipassage.model.entity.Article;
import com.kanade.aipassage.mapper.ArticleMapper;
import com.kanade.aipassage.service.ArticleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.kanade.aipassage.constant.UserConstant.ADMIN_ROLE;

/**
 * 文章表 服务层实现。
 *
 * @author kanade
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article>  implements ArticleService{


    //private final ArticleService articleService;
    private final ArticleAgentService articleAgentService;

    public ArticleServiceImpl( ArticleAgentService articleAgentService) {
        //this.articleService = articleService;
        this.articleAgentService = articleAgentService;
    }

    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限：只能查看自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        return ArticleVO.objToVo(article);
    }
    @Override
    public Article getByTaskId(String taskId) {
        return this.getOne(
                QueryWrapper.create().eq("taskId", taskId)
        );
    }

    @Override
    public void updateArticleStatus(String taskId, ArticleStatusEnum status, String message) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setStatus(status.getValue());
        article.setErrorMessage(message);
        this.updateById(article);

        log.info("文章状态已更新, taskId={}, status={}", taskId, status.getValue());
    }

    @Override
    public void saveArticleContent(String taskId, ArticleState state) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());

        // 保存封面图 URL（从 images 列表中提取 position=1 的 URL）
        if (state.getImages() != null && !state.getImages().isEmpty()) {
            ArticleState.ImageResult cover = state.getImages().stream()
                    .filter(img -> img.getPosition() != null && img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if (cover != null && cover.getUrl() != null) {
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());

        this.updateById(article);
        log.info("文章保存成功, taskId={}", taskId);
    }

    @Override
    public void confirmTitle(String task, String mainTitle, String subTitle, String userDescription, User loginUser) {
        Article article = getByTaskId(task);
        ThrowUtils.throwIf(article == null,ErrorCode.PARAMS_ERROR);

        checkArticlePermission(article,loginUser);

        ArticlePhaseEnum anEnum = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(anEnum != ArticlePhaseEnum.TITLE_SELECTING,ErrorCode.OPERATION_ERROR);
        article.setMainTitle(mainTitle);
        article.setSubTitle(subTitle);
        article.setUserDescription(userDescription);
        article.setPhase(ArticlePhaseEnum.OUTLINE_GENERATING.getValue());

        this.updateById(article);
        log.info("确认标题");

    }

    @Override
    public void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限
        checkArticlePermission(article, loginUser);

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING,
                ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户编辑后的大纲
        article.setOutline(GsonUtils.toJson(outline));
        article.setPhase(ArticlePhaseEnum.CONTENT_GENERATING.getValue());

        this.updateById(article);
    }

    @Override
    public List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null,ErrorCode.NOT_FOUND_ERROR);
        checkArticlePermission(article,loginUser);

        ArticlePhaseEnum articlePhaseEnum = ArticlePhaseEnum.getByValue(article.getPhase());

        ThrowUtils.throwIf(articlePhaseEnum.getValue() != ArticlePhaseEnum.OUTLINE_EDITING.getValue(),ErrorCode.OPERATION_ERROR);

        List<ArticleState.OutlineSection> current = GsonUtils.fromJson(article.getOutline(),new TypeToken<List<ArticleState.OutlineSection>>(){});

        List<ArticleState.OutlineSection> modifyOutline = articleAgentService.aiModifyOutline(article.getMainTitle(),article.getSubTitle(),current,modifySuggestion);

        return modifyOutline;
    }

    @Override
    public void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions) {
        Article article = getByTaskId(taskId);
        if (taskId == null){
            log.error("文章不存在");
            return;
        }
        article.setTitleOptions(GsonUtils.toJson(titleOptions));

        updateById(article);
        log.info("标题保存");
    }

    @Override
    public void updatePhase(String taskId, ArticlePhaseEnum articlePhaseEnum) {
        Article article = getByTaskId(taskId);
        if (article == null){
            log.error("article is not exist");
        return;
        }

        article.setPhase(articlePhaseEnum.getValue());

        updateById(article);
        log.info("update article state");

    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 在同一事务中：先扣配额，再创建任务
        // 如果任务创建失败，配额会自动回滚
        // quotaService.checkAndConsumeQuota(loginUser);
        return createArticleTask(topic, style, enabledImageMethods, loginUser);
    }

    @Override
    public String createArticleTask(String topic, String style,List<String> enabledImageMethods, User loginUser) {

        List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);

        // 校验配图方式权限（普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM）
        validateImageMethods(finalImageMethods, loginUser);

        // 生成任务ID
        String taskId = IdUtil.simpleUUID();

        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStyle(style);
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());
        article.setEnabledImageMethods(finalImageMethods != null && !finalImageMethods.isEmpty()
                ? GsonUtils.toJson(finalImageMethods) : null);
        this.save(article);

        log.info("文章任务已创建, taskId={}, userId={}", taskId, loginUser.getId());
        return taskId;
    }


    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser) {
        long current = request.getPageNum();
        long size = request.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .orderBy("createTime", false);

        // 非管理员只能查看自己的文章
        if (!ADMIN_ROLE.equals(loginUser.getUserRole())) {
            queryWrapper.eq("userId", loginUser.getId());
        } else if (request.getUserId() != null) {
            queryWrapper.eq("userId", request.getUserId());
        }

        // 按状态筛选
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", request.getStatus());
        }

        // 分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为 VO
        return convertToVOPage(articlePage);
    }

    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }

    @Override
    public boolean deleteArticle(Long id, User loginUser) {
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR);

        // 校验权限：只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        // 逻辑删除
        return this.removeById(id);
    }

    private void checkArticlePermission(Article article, User loginUser) {
        if (article == null || loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (ADMIN_ROLE.equals(loginUser.getUserRole())) {
            return;
        }
        if (!article.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作此文章");
        }
    }
    private List<String> processImageMethods(List<String> enabledImageMethods, User loginUser) {
        // 如果用户已选择，直接返回
        if (enabledImageMethods != null && !enabledImageMethods.isEmpty()) {
            return enabledImageMethods;
        }

        // VIP 和管理员：不限制，返回 null 表示支持所有方式
//        if (isVipOrAdmin(loginUser)) {
//            return null;
//        }

        // 普通用户：返回默认的非 VIP 方式
        return List.of(
                ImageMethodEnum.PEXELS.getValue(),
                ImageMethodEnum.MERMAID.getValue(),
                ImageMethodEnum.ICONIFY.getValue(),
                ImageMethodEnum.EMOJI_PACK.getValue()
        );
    }
    private void validateImageMethods(List<String> enabledImageMethods, User loginUser) {
        if (enabledImageMethods == null || enabledImageMethods.isEmpty()) {
            return;
        }

//        // VIP 和管理员无限制
//        if (isVipOrAdmin(loginUser)) {
//            return;
//        }

        // 普通用户限制
        for (String method : enabledImageMethods) {
            if (ImageMethodEnum.NANO_BANANA.getValue().equals(method) ||
                    ImageMethodEnum.SVG_DIAGRAM.getValue().equals(method)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,
                        "高级配图功能（AI 生图、SVG 图表）仅限 VIP 会员使用");
            }
        }
    }
}
