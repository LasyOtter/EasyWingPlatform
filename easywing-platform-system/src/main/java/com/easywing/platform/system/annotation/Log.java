package com.easywing.platform.system.annotation;

import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.enums.OperatorType;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    String title() default "";
    BusinessType businessType() default BusinessType.OTHER;
    OperatorType operatorType() default OperatorType.MANAGE;
    boolean isSaveRequestData() default true;
    boolean isSaveResponseData() default true;
    String[] excludeParamNames() default {"password", "oldPassword", "newPassword", "confirmPassword"};
}
