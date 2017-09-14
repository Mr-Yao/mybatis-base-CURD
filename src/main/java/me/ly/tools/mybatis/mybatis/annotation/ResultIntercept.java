package me.ly.tools.mybatis.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 是否对 mybatis 的结果集拦截处理
 *
 * @author Created by LiYao on 2017-09-14 10:32.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ResultIntercept {

    boolean intercept() default true;
}
