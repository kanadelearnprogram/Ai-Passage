package com.kanade.aipassage.service;

import com.kanade.aipassage.common.auth.LoginUser;
import com.kanade.aipassage.common.messaging.SaveResultRequest;
import com.kanade.aipassage.model.dto.ArticleQueryRequest;
import com.kanade.aipassage.model.dto.ArticleState;
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

    ArticleVO getArticleDetail(String taskId, LoginUser loginUser);

    String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, LoginUser loginUser);

    boolean deleteArticle(Long id, LoginUser loginUser);

    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, LoginUser loginUser);

    Article getByTaskId(String taskId);

    void updateArticleStatus(String taskId, ArticleStatusEnum articleStatusEnum, String message);

    void confirmTitle(String task,String mainTitle,String subTitle, String userDescription, LoginUser loginUser);

    void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, LoginUser loginUser);

    List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, LoginUser loginUser);

    void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions);

    void saveOutline(String taskId, List<ArticleState.OutlineSection> outlineSections);

    void saveContent(String taskId, SaveResultRequest request);

    void updatePhase(String taskId, ArticlePhaseEnum articlePhaseEnum);
}
