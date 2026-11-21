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
 * V2014 模组参数结构体（{@code ModuleInfo}），E 系列 OE/DA 等参数。
 */
public class ModuleInfo {
    /** 基色 1/2/3 */
    public int edbcolor;
    /** 译码方式：0 译码，1 直译 */
    public int edecode;
    /** OE：1 有效，0 无效 */
    public int eoe;
    /** DA：1 高有效，0 低有效 */
    public int eda;
    /** 级联方向。 */
    public int eescade = 0;
    /** 数据方向。 */
    public int edatadirect = 0;
    /** 数据接口，默认 0x10 */
    public int einterfacedata = 0x10;
    /** 红绿交换：0 R+G，1 G+R */
    public int erg;

    /** 默认构造器。 */
    public ModuleInfo() {}
}
