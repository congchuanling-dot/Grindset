package com.grindset.backend.common;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器。
 * 生成 64 位 long:0 | 41位时间戳 | 10位机器号 | 12位序列号
 * - 时间戳:当前毫秒 - 起始纪元,保证趋势递增
 * - 机器号:区分不同机器(单机固定为 1)
 * - 序列号:同一毫秒内自增,最多 4096 个
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始纪元:2024-01-01 00:00:00 的毫秒值。从这天开始算,41 位可用约 69 年。 */
    private static final long EPOCH = 1704067200000L;

    /** 各段占的位数 */
    private static final long MACHINE_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    /** 各段的最大值(用位运算算出:1 左移 n 位再减 1,得到 n 个 1) */
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_BITS); // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);  // 4095

    /** 各段相对最低位要左移多少位 */
    private static final long MACHINE_SHIFT = SEQUENCE_BITS;                 // 机器号在序列号左边
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS; // 时间戳在最左

    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        // 单机 MVP 固定机器号 1;多机部署时应从配置读取并保证各机不同
        long id = 1L;
        if (id < 0 || id > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("机器号须在 0~" + MAX_MACHINE_ID + " 之间");
        }
        this.machineId = id;
    }

    /** 生成下一个唯一 ID。synchronized 保证多线程下序列号不乱。 */
    public synchronized long nextId() {
        long now = System.currentTimeMillis();

        // 时钟回拨检测:当前时间比上次还早,说明机器时间被调回,拒绝生成以防重复
        if (now < lastTimestamp) {
            throw new IllegalStateException(
                    "时钟回拨,拒绝生成 ID,回拨毫秒数=" + (lastTimestamp - now));
        }

        if (now == lastTimestamp) {
            // 同一毫秒内:序列号 +1。& MAX_SEQUENCE 实现"超过 4095 就回到 0"
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 这一毫秒的 4096 个用完了,自旋等到下一毫秒
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            // 进入新的一毫秒,序列号归零
            sequence = 0L;
        }

        lastTimestamp = now;

        // 把三段拼成一个 long:各自左移到自己的位置,再用 | 合并
        return ((now - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_SHIFT)
                | sequence;
    }

    /** 自旋直到进入下一毫秒 */
    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
