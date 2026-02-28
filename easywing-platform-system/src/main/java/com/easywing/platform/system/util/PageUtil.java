package com.easywing.platform.system.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageUtil {

    public static <T, R> Page<R> convert(Page<T> source, Function<T, R> converter) {
        Page<R> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList()));
        return target;
    }

    public static <T, R> List<R> convertList(List<T> source, Function<T, R> converter) {
        if (CollectionUtils.isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream().map(converter).collect(Collectors.toList());
    }
}
