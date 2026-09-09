package com.dongqh.userservice.domain.query;

import com.hmall.common.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    private String key;

    private Integer status;

    private String role;
}
