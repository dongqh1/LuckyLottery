package com.dongqh.membershipservice.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.membershipservice.membership.entity.MembershipGrantRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MembershipGrantRecordMapper extends BaseMapper<MembershipGrantRecord> {

    @Select("SELECT * FROM membership_grant_record WHERE business_no = #{businessNo} FOR UPDATE")
    MembershipGrantRecord selectByBusinessNoForUpdate(@Param("businessNo") String businessNo);
}
