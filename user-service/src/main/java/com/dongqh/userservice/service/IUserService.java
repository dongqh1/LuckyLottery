package com.dongqh.userservice.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dongqh.userservice.domain.dto.LoginFormDTO;
import com.dongqh.userservice.domain.dto.UserAdminFormDTO;
import com.dongqh.userservice.domain.po.User;
import com.dongqh.userservice.domain.query.UserPageQuery;
import com.dongqh.userservice.domain.vo.UserAdminVO;
import com.dongqh.userservice.domain.vo.UserLoginVO;
import com.hmall.common.domain.PageDTO;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface IUserService extends IService<User> {

    UserLoginVO login(LoginFormDTO loginFormDTO);

    void deductMoney(String pw, Integer totalFee);

    PageDTO<UserAdminVO> queryUsers(UserPageQuery query);

    void createUser(UserAdminFormDTO form);

    void updateUser(UserAdminFormDTO form);

    void deleteUser(Long id);

    void updateStatus(Long id, Integer status);
}
