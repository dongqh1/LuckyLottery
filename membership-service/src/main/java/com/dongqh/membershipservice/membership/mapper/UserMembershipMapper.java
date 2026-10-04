package com.dongqh.membershipservice.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.membershipservice.membership.entity.UserMembership;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMembershipMapper extends BaseMapper<UserMembership> {

    @Select("SELECT * FROM user_membership WHERE user_id = #{userId} FOR UPDATE")
    UserMembership selectByUserIdForUpdate(@Param("userId") long userId);
}
