package com.dongqh.user.domain.query;

import com.hmall.common.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    private String key;

    private Integer status;
}

