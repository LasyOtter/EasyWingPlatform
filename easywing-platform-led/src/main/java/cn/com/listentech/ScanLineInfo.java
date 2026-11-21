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
 * V2014 扫描参数结构体（{@code ScanLineInfo}）。
 */
public class ScanLineInfo {
    /** 模组宽度，默认 -1 */
    public int emodulewidth = -1;
    /** 列点数，默认 0 */
    public int ecloumndot = 0;
    /** 扫描方式：16 扫 0，P16 扫 6，P10 扫 8 */
    public int escanstyle;
    /** 数据高度，默认 -1 */
    public int edtataheight = -1;

    /** 默认构造器。 */
    public ScanLineInfo() {}
}
