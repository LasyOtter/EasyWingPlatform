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
package com.easywing.platform.data.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class PageUtils {

    private PageUtils() {
    }

    /**
     * 构建分页对象
     *
     * @param current 当前页码
     * @param size    每页记录数
     * @param <T>     实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(long current, long size) {
        return new Page<>(current, size);
    }

    /**
     * 构建分页对象（默认每页10条）
     *
     * @param current 当前页码
     * @param <T>     实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(long current) {
        return new Page<>(current, 10);
    }

    /**
     * 将源分页对象转换为目标类型的分页对象
     *
     * @param source   源分页对象
     * @param converter 转换函数
     * @param <T>      源实体类型
     * @param <R>      目标实体类型
     * @return 目标类型的分页对象
     */
    public static <T, R> Page<R> convert(Page<T> source, Function<T, R> converter) {
        Page<R> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setOrders(source.getOrders());
        target.setSearchCount(source.isSearchCount());
        target.setOptimistic(source.isOptimistic());
        if (source.getRecords() != null) {
            target.setRecords(source.getRecords().stream()
                    .map(converter)
                    .collect(Collectors.toList()));
        }
        return target;
    }

    /**
     * 将IPage对象转换为目标类型的分页对象
     *
     * @param source   源分页对象
     * @param converter 转换函数
     * @param <T>      源实体类型
     * @param <R>      目标实体类型
     * @return 目标类型的分页对象
     */
    public static <T, R> IPage<R> convert(IPage<T> source, Function<T, R> converter) {
        Page<R> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setOrders(source.getOrders());
        target.setSearchCount(source.isSearchCount());
        if (source.getRecords() != null) {
            target.setRecords(source.getRecords().stream()
                    .map(converter)
                    .collect(Collectors.toList()));
        }
        return target;
    }

    /**
     * 从IPage获取记录列表
     *
     * @param page 分页对象
     * @param <T> 实体类型
     * @return 记录列表
     */
    public static <T> List<T> getRecords(IPage<T> page) {
        return page != null ? page.getRecords() : null;
    }

    /**
     * 构建空分页对象
     *
     * @param current 当前页码
     * @param size    每页记录数
     * @param <T>     实体类型
     * @return 空分页对象
     */
    public static <T> Page<T> emptyPage(long current, long size) {
        Page<T> page = new Page<>(current, size, 0);
        page.setRecords(List.of());
        return page;
    }
}
