package com.kanade.aipassage.service;

import com.kanade.aipassage.model.dto.UserQueryRequest;
import com.kanade.aipassage.model.vo.LoginUserVO;
import com.kanade.aipassage.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.kanade.aipassage.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author kanade
 */
public interface UserService extends IService<User> {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    LoginUserVO getLoginUserVO(User loginUser);

    boolean userLogout(HttpServletRequest request);

    String getEncryptPassword(String defaultPassword);

    UserVO getUserVO(User user);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    List<UserVO> getUserVOList(List<User> records);
}
