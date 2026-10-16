package com.dongqh.luckyhub.mapper.rbac;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.domain.po.rbac.SysUser;
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

}


