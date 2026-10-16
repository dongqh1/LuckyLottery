package com.dongqh.luckyhub.service.lottery;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.domain.dto.lottery.DrawOrderQuery;
import com.dongqh.luckyhub.domain.dto.lottery.DrawRecordQuery;
import com.dongqh.luckyhub.domain.vo.lottery.DrawOrderView;
import com.dongqh.luckyhub.domain.vo.lottery.DrawRecordView;
import com.dongqh.luckyhub.domain.vo.lottery.LotteryActivityView;
import com.dongqh.luckyhub.domain.vo.lottery.LotteryPrizeView;

import java.util.List;

public interface LotteryQueryService {
    List<LotteryActivityView> listActivities();
    List<LotteryPrizeView> listPrizes(long activityId);
    LotteryActivityView getActivity(long activityId);
    DrawOrderView getDraw(String requestId);
    PageResponse<DrawOrderView> pageOrders(DrawOrderQuery query);
    PageResponse<DrawOrderView> pageMyOrders(long userId, DrawOrderQuery query);
    PageResponse<DrawRecordView> pageRecords(DrawRecordQuery query);
}
