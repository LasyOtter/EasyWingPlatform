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
package cn.com.listentech;

/**
 * V2014 屏参结构体（{@code ScreenInfo}）。
 *
 * <p>字段与 64 位官方 {@code LEDControl.SetScreenInfo(int, ScreenInfo)} 对应，
 * 包级可见即可（官方 class 也是 package 级）。
 */
public class ScreenInfo {
    /** 屏宽。 */
    public int screenwidth;
    /** 屏高。 */
    public int screenheight;
    /** 基色：1 单色 2 双色 3 三基色 */
    public int dbcolor;
    /** 波特率。 */
    public int baud;
    /** 屏名。 */
    public String screenname;

    /** 默认构造器。 */
    public ScreenInfo() {}

    /**
     * 全参构造器。
     *
     * @param screenwidth 屏宽
     * @param screenheight 屏高
     * @param dbcolor 基色
     * @param baud 波特率
     * @param screenname 屏名
     */
    public ScreenInfo(int screenwidth, int screenheight, int dbcolor, int baud, String screenname) {
        this.screenwidth = screenwidth;
        this.screenheight = screenheight;
        this.dbcolor = dbcolor;
        this.baud = baud;
        this.screenname = screenname;
    }
}
