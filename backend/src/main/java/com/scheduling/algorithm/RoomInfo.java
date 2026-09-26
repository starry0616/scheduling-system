package com.scheduling.algorithm;

/**
 * 教室的算法层轻量描述(纯 POJO, 与 JPA Entity 解耦)。
 *
 * <p>由 Service 组装阶段从 classroom 实体转换而来, 只保留候选生成所需的三个属性。
 *
 * @param id       教室 id(classroom.id)
 * @param capacity 教室容量
 * @param roomType 教室类型: NORMAL / MULTIMEDIA / LAB
 */
public record RoomInfo(long id, int capacity, String roomType) {

    public RoomInfo {
        if (capacity <= 0) {
            throw new IllegalArgumentException("教室容量必须 > 0: id=" + id + ", capacity=" + capacity);
        }
    }
}
