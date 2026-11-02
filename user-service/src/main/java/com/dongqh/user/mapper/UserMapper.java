package com.dongqh.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.user.domain.po.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT status FROM user WHERE id = #{id} LIMIT 1")
    Integer selectStatusValueById(@Param("id") Long id);

    @Update("UPDATE user SET balance = balance - #{totalFee} " +
            "WHERE id = #{userId} AND #{totalFee} > 0 AND balance >= #{totalFee}")
    int updateMoney(@Param("userId") Long userId, @Param("totalFee") Integer totalFee);
}
