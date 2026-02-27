package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RouterVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String name;
    private String path;
    private Boolean hidden;
    private String redirect;
    private String component;
    private Boolean affix;
    private MetaVO meta;
    private List<RouterVO> children;

    @Data
    public static class MetaVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String title;
        private String icon;
        private Boolean noCache;
        private String link;
        private Boolean hidden;
        private Boolean affix;
    }
}
