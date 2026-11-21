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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 矩形区域：left, top, width, height（屏坐标，单位像素）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rect {

    private int left;
    private int top;
    private int width;
    private int height;

    public int right() {
        return left + width;
    }

    public int bottom() {
        return top + height;
    }

    /** 两矩形是否重叠（边相接不算重叠）。 */
    public boolean overlaps(Rect o) {
        return this.left < o.right() && o.left < this.right() && this.top < o.bottom() && o.top < this.bottom();
    }

    @Override
    public String toString() {
        return "[" + left + "," + top + "," + width + "," + height + "]";
    }
}
