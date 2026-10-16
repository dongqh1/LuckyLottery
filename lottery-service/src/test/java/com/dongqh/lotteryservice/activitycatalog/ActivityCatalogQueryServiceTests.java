package com.dongqh.lotteryservice.activitycatalog;

import com.dongqh.lotteryservice.domain.po.activitycatalog.ActivityCatalogRow;
import com.dongqh.lotteryservice.domain.po.activitycatalog.ActivityPrizeCatalogRow;
import com.dongqh.lotteryservice.service.activitycatalog.ActivityCatalogQueryService;
import com.dongqh.lotteryservice.domain.vo.activitycatalog.ActivityCatalogView;
import com.dongqh.lotteryservice.domain.vo.activitycatalog.ActivityPrizeCatalogView;
import com.dongqh.lotteryservice.enums.activityprize.ActivityStatus;
import com.dongqh.lotteryservice.enums.activityprize.PrizeType;
import com.dongqh.lotteryservice.mapper.activityprize.ActivityCatalogQueryMapper;
import com.dongqh.lotteryservice.exception.LotteryDomainException;
import com.dongqh.lotteryservice.enums.LotteryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityCatalogQueryServiceTests {

    private ActivityCatalogQueryMapper mapper;
    private ActivityCatalogQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ActivityCatalogQueryMapper.class);
        service = new ActivityCatalogQueryService(mapper);
    }

    @Test
    void listActivitiesPassesCurrentTimeAndProjectsLegacyFields() {
        ActivityCatalogRow row = activity();
        when(mapper.listActivities(any(LocalDateTime.class))).thenReturn(List.of(row));
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        List<ActivityCatalogView> views = service.listActivities();

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).listActivities(now.capture());
        assertThat(now.getValue()).isBetween(before, after);
        assertThat(views).containsExactly(new ActivityCatalogView(23L, "Summer Draw", "Seasonal",
                ActivityStatus.RUNNING, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0), 5));
    }

    @Test
    void getActivityProjectsExistingActivityFieldsRegardlessOfStatus() {
        ActivityCatalogRow row = activity();
        row.setStatus(ActivityStatus.ENDED);
        when(mapper.findActivityById(23L)).thenReturn(row);

        assertThat(service.getActivity(23L)).isEqualTo(new ActivityCatalogView(23L, "Summer Draw", "Seasonal",
                ActivityStatus.ENDED, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0), 5));
        verify(mapper).findActivityById(23L);
    }

    @Test
    void getActivityUsesLotteryActivityNotFoundContract() {
        when(mapper.findActivityById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getActivity(404L))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.ACTIVITY_NOT_FOUND));
    }

    @Test
    void listPrizesRequiresActivityThenProjectsOnlyPublicPrizeFields() {
        when(mapper.findActivityById(23L)).thenReturn(activity());
        when(mapper.listVisiblePrizes(23L)).thenReturn(List.of(prize(7L, 1, 900L), prize(8L, 2, null)));

        List<ActivityPrizeCatalogView> views = service.listPrizes(23L);

        assertThat(views).containsExactly(
                new ActivityPrizeCatalogView(7L, "Gift", PrizeType.PHYSICAL, "/gift.png", 1, 900L),
                new ActivityPrizeCatalogView(8L, "Points", PrizeType.POINTS, "/points.png", 2, null));
        verify(mapper).findActivityById(23L);
        verify(mapper).listVisiblePrizes(23L);
    }

    @Test
    void listPrizesDoesNotQueryPrizesWhenActivityIsMissing() {
        when(mapper.findActivityById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.listPrizes(404L))
                .isInstanceOfSatisfying(LotteryDomainException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(LotteryErrorCode.ACTIVITY_NOT_FOUND));
        verify(mapper, never()).listVisiblePrizes(anyLong());
    }

    @Test
    void listPrizesDoesNotApplyASecondClientSideLimit() {
        when(mapper.findActivityById(23L)).thenReturn(activity());
        List<ActivityPrizeCatalogRow> rows = new ArrayList<>();
        for (long id = 1; id <= 101; id++) {
            rows.add(prize(id, (int) id, null));
        }
        when(mapper.listVisiblePrizes(23L)).thenReturn(rows);

        assertThat(service.listPrizes(23L)).hasSize(101);
    }

    private ActivityCatalogRow activity() {
        ActivityCatalogRow row = new ActivityCatalogRow();
        row.setId(23L);
        row.setActivityName("Summer Draw");
        row.setDescription("Seasonal");
        row.setStatus(ActivityStatus.RUNNING);
        row.setStartTime(LocalDateTime.of(2026, 9, 1, 0, 0));
        row.setEndTime(LocalDateTime.of(2026, 10, 1, 0, 0));
        row.setDailyLimit(5);
        return row;
    }

    private ActivityPrizeCatalogRow prize(long id, int sortOrder, Long hmallItemId) {
        ActivityPrizeCatalogRow row = new ActivityPrizeCatalogRow();
        row.setPrizeId(id);
        row.setPrizeName(id == 8L ? "Points" : "Gift");
        row.setPrizeType(id == 8L ? PrizeType.POINTS : PrizeType.PHYSICAL);
        row.setImageUrl(id == 8L ? "/points.png" : "/gift.png");
        row.setSortOrder(sortOrder);
        row.setHmallItemId(hmallItemId);
        return row;
    }
}
