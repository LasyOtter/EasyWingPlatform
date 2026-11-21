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
package com.listenvision;

/**
 * 广域网心跳服务回调（Msg: 1=控制卡连上, 2=断开）。
 */
public abstract class ledCallBack {

    /** 心跳回调。 */
    public abstract void LedServerCallback(int Msg, int wParam, String networkIdStr);
}
