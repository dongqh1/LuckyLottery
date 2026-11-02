package com.dongqh.membership.mapper.membership;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.membership.domain.po.membership.UserMembership;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMembershipMapper extends BaseMapper<UserMembership> {

    @Select("SELECT * FROM user_membership WHERE user_id = #{userId} FOR UPDATE")
    UserMembership selectByUserIdForUpdate(@Param("userId") long userId);
}
