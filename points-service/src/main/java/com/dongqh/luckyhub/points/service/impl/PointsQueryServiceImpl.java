package com.dongqh.luckyhub.service.points.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dongqh.luckyhub.domain.dto.points.PointsLedgerQuery;
import com.dongqh.luckyhub.domain.po.points.PointsLedger;
import com.dongqh.luckyhub.mapper.points.PointsLedgerMapper;
import com.dongqh.luckyhub.service.points.PointsAccountService;
import com.dongqh.luckyhub.service.points.PointsQueryService;
import com.dongqh.luckyhub.domain.vo.points.PointsAccountView;
import com.dongqh.luckyhub.domain.vo.points.PointsLedgerView;
import com.dongqh.luckyhub.domain.vo.points.PointsPageResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PointsQueryServiceImpl implements PointsQueryService {

    private final PointsAccountService accountService;
    private final PointsLedgerMapper ledgerMapper;

    public PointsQueryServiceImpl(
            PointsAccountService accountService,
            PointsLedgerMapper ledgerMapper
    ) {
        this.accountService = accountService;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public PointsAccountView getAccount(long userId) {
        return accountService.get(userId);
    }

    @Override
    public PointsPageResponse<PointsLedgerView> pageLedgers(long userId, PointsLedgerQuery query) {
        String businessId = StringUtils.hasText(query.getBusinessId())
                ? query.getBusinessId().trim() : null;
        LambdaQueryWrapper<PointsLedger> wrapper = new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getUserId, userId)
                .eq(businessId != null, PointsLedger::getBusinessId, businessId)
                .eq(query.getBusinessType() != null,
                        PointsLedger::getBusinessType, query.getBusinessType())
                .eq(query.getDirection() != null,
                        PointsLedger::getDirection, query.getDirection())
                .orderByDesc(PointsLedger::getCreatedAt)
                .orderByDesc(PointsLedger::getId);
        Page<PointsLedger> result = ledgerMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PointsLedgerView> records = result.getRecords().stream().map(this::view).toList();
        return new PointsPageResponse<>(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    private PointsLedgerView view(PointsLedger ledger) {
        return new PointsLedgerView(
                ledger.getId(), ledger.getUserId(), ledger.getBusinessType(),
                ledger.getBusinessId(), ledger.getDirection(), ledger.getAmount(),
                ledger.getBalanceAfter(), ledger.getReversalOfLedgerId(),
                ledger.getRemark(), ledger.getCreatedAt());
    }
}
