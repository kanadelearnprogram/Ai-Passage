package com.kanade.aipassage.service;

import com.kanade.aipassage.model.dto.ArticleQueryRequest;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.entity.User;
import com.kanade.aipassage.model.enums.ArticlePhaseEnum;
import com.kanade.aipassage.model.enums.ArticleStatusEnum;
import com.kanade.aipassage.model.vo.ArticleVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.kanade.aipassage.model.entity.Article;

import java.util.List;

/**
 * 文章表 服务层。
 *
 * @author kanade
 */
public interface ArticleService extends IService<Article> {

    ArticleVO getArticleDetail(String taskId, User loginUser);

    String createArticleTask(String topic, String style,List<String> enabledImageMethods, User loginUser);

    boolean deleteArticle(Long id, User loginUser);

    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    Article getByTaskId(String taskId);

    void updateArticleStatus(String taskId, ArticleStatusEnum articleStatusEnum, String message);

    void saveArticleContent(String taskId, ArticleState state);

    void confirmTitle(String task,String mainTitle,String subTitle, String userDescription, User loginUser);

    void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, User loginUser);

    List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, User loginUser);

    void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions);

    void updatePhase(String taskId, ArticlePhaseEnum articlePhaseEnum);

    String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser);
}
