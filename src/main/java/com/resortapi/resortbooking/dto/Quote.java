package com.resortapi.resortbooking.dto;

import java.math.BigDecimal;

/** BR-05: nights and the summed nightly prices for one stay. */
public record Quote(long nights, BigDecimal total) {
}
