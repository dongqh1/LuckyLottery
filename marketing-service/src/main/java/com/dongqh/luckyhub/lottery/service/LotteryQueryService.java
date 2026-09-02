package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.dto.DrawRecordQuery;
import com.dongqh.luckyhub.lottery.vo.DrawOrderView;
import com.dongqh.luckyhub.lottery.vo.DrawRecordView;
import com.dongqh.luckyhub.lottery.vo.LotteryActivityView;
import com.dongqh.luckyhub.lottery.vo.LotteryPrizeView;

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
