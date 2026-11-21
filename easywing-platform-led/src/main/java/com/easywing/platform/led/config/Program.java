/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.led.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 节目（programs 列表项）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class Program {

    /** 0-255 */
    private int programNo;
    /** 播放时长；0 = 不限 */
    private int playTime;
    /** 1-255 */
    private int loopCount = 1;
    /** 定时播放（可选） */
    private ProgramSchedule schedule;

    private List<Area> areas = new ArrayList<>();

    /** 返回指定类型的所有区域（按添加顺序）。 */
    public List<Area> areasOfType(String type) {
        List<Area> result = new ArrayList<>();
        for (Area a : areas) {
            if (a.getType() != null && a.getType().equalsIgnoreCase(type)) {
                result.add(a);
            }
        }
        return result;
    }

    /**
     * 节目定时。
     */
    @Data
    public static class ProgramSchedule {
        private boolean enable;
        /** 定时开关位标志：ENABLE_DATE=0x01 | ENABLE_TIME=0x02 | ENABLE_WEEK=0x04 */
        private int enableFlag = 0x01 | 0x02 | 0x04;
        /** 星期位标志：WEEK_MON=0x02 ... WEEK_SUN=0x01，按位或组合；全周 = 0x7f */
        private int weekValue = 0x7f;

        private String start; // "yyyy-MM-dd HH:mm:ss"
        private String end; // "yyyy-MM-dd HH:mm:ss"
    }
}
