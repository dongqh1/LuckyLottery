package com.dongqh.userservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.dongqh.userservice.config.JwtProperties;
import com.dongqh.userservice.domain.dto.LoginFormDTO;
import com.dongqh.userservice.domain.dto.UserAdminFormDTO;
import com.dongqh.userservice.domain.po.User;
import com.dongqh.userservice.domain.query.UserPageQuery;
import com.dongqh.userservice.domain.vo.UserAdminVO;
import com.dongqh.userservice.domain.vo.UserLoginVO;
import com.dongqh.userservice.enums.UserStatus;
import com.dongqh.userservice.mapper.UserMapper;
import com.dongqh.userservice.service.IUserService;
import com.dongqh.userservice.utils.JwtTool;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final PasswordEncoder passwordEncoder;

    private final JwtTool jwtTool;

    private final JwtProperties jwtProperties;

    @Override
    public UserLoginVO login(LoginFormDTO loginDTO) {
        // 1.数据校验
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        // 2.根据用户名或手机号查询
        User user = lambdaQuery().eq(User::getUsername, username).one();
        Assert.notNull(user, "用户名错误");
        // 3.校验是否禁用
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new ForbiddenException("用户被冻结");
        }
        // 4.校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 5.生成TOKEN
        String token = jwtTool.createToken(user.getId(), user.getRole(), jwtProperties.getTokenTTL());
        // 6.封装VO返回
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setBalance(user.getBalance());
        vo.setToken(token);
        return vo;
    }

    @Override
    public void deductMoney(String pw, Integer totalFee) {
        log.info("开始扣款");
        // 1.校验密码
        User user = getById(UserContext.getUser());
        if(user == null || !passwordEncoder.matches(pw, user.getPassword())){
            // 密码错误
            throw new BizIllegalException("用户密码错误");
        }

        // 2.尝试扣款
        if (totalFee == null || totalFee <= 0) {
            throw new BizIllegalException("扣款金额必须为正数");
        }
        int affectedRows;
        try {
            affectedRows = baseMapper.updateMoney(UserContext.getUser(), totalFee);
        } catch (Exception e) {
            throw new BizIllegalException("扣款失败！", e);
        }
        if (affectedRows != 1) {
            throw new BizIllegalException("扣款失败，可能是余额不足！");
        }
        log.info("扣款成功");
    }

    @Override
    public PageDTO<UserAdminVO> queryUsers(UserPageQuery query) {
        requireAdmin();
        Page<User> page = baseMapper.selectPage(query.toMpPage("create_time", false),
                Wrappers.<User>lambdaQuery()
                        .and(StrUtil.isNotBlank(query.getKey()), wrapper -> wrapper
                                .like(User::getUsername, query.getKey())
                                .or()
                                .like(User::getPhone, query.getKey()))
                        .eq(query.getStatus() != null, User::getStatus,
                                query.getStatus() == null ? null : normalizeStatus(query.getStatus()))
                        .eq(StrUtil.isNotBlank(query.getRole()), User::getRole,
                                normalizeRole(query.getRole())));
        return PageDTO.of(page, user -> {
            UserAdminVO view = BeanUtils.copyBean(user, UserAdminVO.class);
            view.setStatus(user.getStatus() == UserStatus.NORMAL ? 1 : 2);
            return view;
        });
    }

    @Override
    public void createUser(UserAdminFormDTO form) {
        requireAdmin();
        if (StrUtil.isBlank(form.getUsername()) || StrUtil.isBlank(form.getPhone())
                || StrUtil.isBlank(form.getPassword())) {
            throw new BadRequestException("用户名、手机号和密码不能为空");
        }
        User user = new User()
                .setUsername(form.getUsername().trim())
                .setPhone(form.getPhone().trim())
                .setPassword(passwordEncoder.encode(form.getPassword()))
                .setRole(normalizeRole(form.getRole()))
                .setStatus(form.getStatus() == null
                        ? UserStatus.NORMAL : normalizeStatus(form.getStatus()));
        if (baseMapper.insert(user) != 1) {
            throw new BizIllegalException("新增用户失败");
        }
    }

    @Override
    public void updateUser(UserAdminFormDTO form) {
        requireAdmin();
        if (form.getId() == null) {
            throw new BadRequestException("用户ID不能为空");
        }
        User user = new User()
                .setId(form.getId())
                .setUsername(trimToNull(form.getUsername()))
                .setPhone(trimToNull(form.getPhone()));
        if (StrUtil.isNotBlank(form.getPassword())) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        if (StrUtil.isNotBlank(form.getRole())) {
            user.setRole(normalizeRole(form.getRole()));
        }
        if (form.getStatus() != null) {
            user.setStatus(normalizeStatus(form.getStatus()));
        }
        if (baseMapper.updateById(user) != 1) {
            throw new BizIllegalException("更新用户失败");
        }
    }

    @Override
    public void deleteUser(Long id) {
        requireAdmin();
        if (id == null) {
            throw new BadRequestException("用户ID不能为空");
        }
        if (id.equals(UserContext.getUser())) {
            throw new BizIllegalException("不能删除当前登录管理员");
        }
        if (baseMapper.deleteById(id) != 1) {
            throw new BizIllegalException("删除用户失败");
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        requireAdmin();
        if (id == null || status == null) {
            throw new BadRequestException("用户ID和状态不能为空");
        }
        User user = new User().setId(id).setStatus(normalizeStatus(status));
        if (baseMapper.updateById(user) != 1) {
            throw new BizIllegalException("更新用户状态失败");
        }
    }

    private void requireAdmin() {
        Long userId = UserContext.getUser();
        User current = userId == null ? null : baseMapper.selectById(userId);
        if (current == null || current.getStatus() != UserStatus.NORMAL
                || !"ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new ForbiddenException("仅管理员可执行该操作");
        }
    }

    private UserStatus normalizeStatus(Integer status) {
        return status == 2 ? UserStatus.FROZEN : UserStatus.of(status);
    }

    private String normalizeRole(String role) {
        String normalized = StrUtil.isBlank(role) ? "USER" : role.trim().toUpperCase();
        if (!"USER".equals(normalized) && !"ADMIN".equals(normalized)) {
            throw new BadRequestException("用户角色仅支持 USER 或 ADMIN");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
