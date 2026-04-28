package com.easywing.platform.ai.skill;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Skill {
    String name();
    String description() default "";
    String category() default "custom";
    int priority() default 0;
}