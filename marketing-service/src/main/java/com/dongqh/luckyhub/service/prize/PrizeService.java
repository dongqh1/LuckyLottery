package com.dongqh.luckyhub.service.prize;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.prize.CreatePrizeCommand;
import com.dongqh.luckyhub.domain.dto.prize.PrizeQuery;
import com.dongqh.luckyhub.domain.dto.prize.UpdatePrizeCommand;
import com.dongqh.luckyhub.domain.vo.prize.PrizeView;

public interface PrizeService {

    PrizeView create(CreatePrizeCommand command);

    PrizeView getById(long id);

    PageResponse<PrizeView> page(PrizeQuery query);

    PrizeView update(long id, UpdatePrizeCommand command);

    void disable(long id);
}
