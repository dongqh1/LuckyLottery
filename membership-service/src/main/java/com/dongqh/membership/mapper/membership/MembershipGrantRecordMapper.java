package com.dongqh.membership.mapper.membership;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.membership.domain.po.membership.MembershipGrantRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MembershipGrantRecordMapper extends BaseMapper<MembershipGrantRecord> {

    @Select("SELECT * FROM membership_grant_record WHERE business_no = #{businessNo} FOR UPDATE")
    MembershipGrantRecord selectByBusinessNoForUpdate(@Param("businessNo") String businessNo);
}
