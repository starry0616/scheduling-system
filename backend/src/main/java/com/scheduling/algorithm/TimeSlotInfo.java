package com.scheduling.algorithm;

import java.util.Objects;

/**
 * 纯算法层时间段信息(与 JPA Entity 解耦)。
 *
 * 时间轴语义: dayOfWeek=1(周一)..5(周五), period=1..5(第1..5大节),
 * 连续性判断必须基于 (dayOfWeek, period), 禁止使用 slotId+1。
 */
public final class TimeSlotInfo {
    private final long id;
    private final int dayOfWeek;
    private final int period;

    public TimeSlotInfo(long id, int dayOfWeek, int period) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.period = period;
    }

    public long getId() {
        return id;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getPeriod() {
        return period;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeSlotInfo that)) {
            return false;
        }
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TimeSlot{id=" + id + ",day=" + dayOfWeek + ",period=" + period + "}";
    }
}
