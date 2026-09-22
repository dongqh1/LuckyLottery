package com.dongqh.luckyhub.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Insert("INSERT IGNORE INTO sys_user " +
            "(id, username, password, nickname, status) VALUES " +
            "(#{userId}, #{username}, '!HMALL_EXTERNAL_IDENTITY!', #{nickname}, 1)")
    int insertHmallProjection(
            @Param("userId") long userId,
            @Param("username") String username,
            @Param("nickname") String nickname
    );

    @Insert("""
            INSERT IGNORE INTO sys_user_role (user_id, role_id)
            SELECT #{userId}, id
              FROM sys_role
             WHERE role_code = 'USER'
               AND status = 1
            """)
    int insertDefaultUserRole(@Param("userId") long userId);
}
