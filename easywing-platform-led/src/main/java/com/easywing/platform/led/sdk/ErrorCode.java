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
package com.easywing.platform.led.sdk;

import java.util.HashMap;
import java.util.Map;

/**
 * 灵信 SDK 错误码语义化。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class ErrorCode {

    private static final Map<Integer, String> MAP = new HashMap<>();

    static {
        MAP.put(-1, "无效句柄");
        MAP.put(-2, "节目已存在");
        MAP.put(-3, "节目不存在");
        MAP.put(-4, "区域不存在");
        MAP.put(-5, "Socket 失败");
        MAP.put(-6, "错误回复");
        MAP.put(-7, "不支持文件类型");
        MAP.put(-8, "IP/网关/掩码/MAC 格式错误");
        MAP.put(-9, "错误波特率");
        MAP.put(-10, "文件路径不存在");
        MAP.put(-11, "区域重叠");
        MAP.put(-12, "打开文件失败");
        MAP.put(-14, "区域已存在");
        MAP.put(-15, "无效发送类型");
        MAP.put(-16, "绘图失败");
        MAP.put(-17, "创建文件夹失败");
        MAP.put(-18, "区域超屏宽");
        MAP.put(-19, "区域超屏高");
        MAP.put(-30, "打开串口失败");
        MAP.put(-31, "串口参数错误");
        MAP.put(-32, "串口发送失败");
        MAP.put(-33, "串口接收失败");
        MAP.put(-34, "串口关闭失败");
        MAP.put(-35, "串口未打开");
        MAP.put(-36, "串口缓冲区溢出");
        MAP.put(-37, "串口超时");
        MAP.put(-38, "发送取消");
        MAP.put(-42, "字体类型不支持或损坏");
        MAP.put(-43, "字体文件打开失败");
        MAP.put(-100, "网络连接失败");
        MAP.put(-101, "网络发送失败");
        MAP.put(-102, "网络接收失败");
        MAP.put(-103, "Bind 失败");
        MAP.put(-104, "无可用网卡");
        MAP.put(-105, "广域网未找到设备");
        MAP.put(-106, "广域网连接超时");
        MAP.put(-107, "广域网客户端断开");
        MAP.put(130, "帧序号错误");
        MAP.put(162, "未定义操作码");
        MAP.put(169, "帧超长");
        MAP.put(49472, "Logo 与屏参不适应");
        MAP.put(54704, "固件型号不匹配");
        MAP.put(54708, "非有效固件程序");
        MAP.put(55992, "屏参不一致");
        MAP.put(49594, "超出带载");
        MAP.put(55989, "节目数据超最大值");
    }

    private ErrorCode() {}

    public static String describe(int code) {
        return MAP.getOrDefault(code, "未知错误");
    }
}
