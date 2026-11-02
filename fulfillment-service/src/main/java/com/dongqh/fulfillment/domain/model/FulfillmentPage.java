package com.dongqh.fulfillment.domain.model;

import java.util.List;

public record FulfillmentPage<T>(List<T> records, long total, long current, long size, long pages) {

    public FulfillmentPage {
        records = List.copyOf(records);
    }
}
