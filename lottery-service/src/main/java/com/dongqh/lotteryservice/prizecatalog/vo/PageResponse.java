package com.dongqh.lotteryservice.prizecatalog.vo;

import java.util.List;

public record PageResponse<T>(List<T> records, long total, long page, long size, long pages) {
}
